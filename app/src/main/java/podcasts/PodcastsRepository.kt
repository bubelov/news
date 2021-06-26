package podcasts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import db.EntryEnclosure
import db.EntryEnclosureQueries
import entries.EntriesRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

class PodcastsRepository(
    private val entryEnclosureQueries: EntryEnclosureQueries,
    private val entriesRepository: EntriesRepository,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    suspend fun getDownloadProgress(entryId: String) = withContext(Dispatchers.IO) {
        entryEnclosureQueries.selectByEntryId(entryId).asFlow().map {
            it.executeAsOneOrNull()?.downloadPercent
        }
    }

    fun selectByEntryId(entryId: String): EntryEnclosure? {
        return entryEnclosureQueries.selectByEntryId(entryId).executeAsOneOrNull()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun download(entryId: String) = withContext(Dispatchers.IO) {
        val entry = entriesRepository.selectById(entryId)

        if (entry == null) {
            entryEnclosureQueries.deleteWhere(entryId = entryId)
            return@withContext
        }

        if (entry.enclosureLink.isBlank()) {
            return@withContext
        }

        val existingEnclosure = entryEnclosureQueries.selectByEntryId(entryId).executeAsOneOrNull()

        if (existingEnclosure != null) {
            return@withContext
        }

        val enclosure = EntryEnclosure(
            entryId = entryId,
            downloadPercent = 0,
            cacheUri = "",
        )

        entryEnclosureQueries.insertOrReplace(enclosure)

        val request = Request.Builder()
            .url(entry.enclosureLink)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            entryEnclosureQueries.deleteWhere(entryId = entryId)
            return@withContext
        }

        val responseBody = response.body

        if (responseBody == null) {
            entryEnclosureQueries.deleteWhere(entryId = entryId)
            return@withContext
        }

        var cacheUri: Uri? = null

        runCatching {
            val fileName = "${UUID.randomUUID()}.${getExtension(entry.enclosureLinkType)}"
            val outputStream: OutputStream

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cacheUri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, entry.enclosureLinkType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PODCASTS)
                        put(MediaStore.MediaColumns.IS_PENDING, true)
                    }
                )

                if (cacheUri == null) {
                    throw Exception("Can't save podcast to media store")
                }

                outputStream = context.contentResolver.openOutputStream(cacheUri!!)!!
            } else {
                val podcastsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
                val file = File(podcastsDirectory, fileName)
                cacheUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            entryEnclosureQueries.setCacheUri(
                cacheUri = cacheUri.toString(),
                entryId = enclosure.entryId,
            )

            val bytesInBody = responseBody.contentLength()

            responseBody.source().use { bufferedSource ->
                outputStream.sink().buffer().use { bufferedSink ->
                    var downloadedBytes = 0L
                    var downloadedPercent: Long
                    var lastReportedDownloadedPercent = 0L

                    while (true) {
                        val buffer = bufferedSource.read(bufferedSink.buffer, 1024L * 16L)
                        bufferedSink.flush()

                        if (buffer == -1L) {
                            break
                        }

                        downloadedBytes += buffer

                        if (downloadedBytes > 0) {
                            downloadedPercent =
                                (downloadedBytes.toDouble() / bytesInBody.toDouble() * 100.0).toLong()

                            if (downloadedPercent > lastReportedDownloadedPercent) {
                                entryEnclosureQueries.setDowloadPercent(
                                    downloadPercent = downloadedPercent,
                                    entryId = enclosure.entryId,
                                )

                                lastReportedDownloadedPercent = downloadedPercent
                            }
                        }
                    }
                }
            }
        }.onSuccess {
            entryEnclosureQueries.setDowloadPercent(
                downloadPercent = 100,
                entryId = enclosure.entryId,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cacheUri?.let {
                    context.contentResolver.update(
                        it,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, false) },
                        null,
                        null
                    )
                }
            }
        }.onFailure {
            Timber.e("Fail")

            entryEnclosureQueries.deleteWhere(entryId = entryId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cacheUri?.let { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
            }

            throw it
        }
    }

    suspend fun deleteIncompleteDownloads() = withContext(Dispatchers.IO) {
        Timber.d("Deleting incomplete downloads")

        entryEnclosureQueries.selectAll().executeAsList().forEach { metadata ->
            if (metadata.cacheUri.isEmpty()) {
                Timber.d("Cache URI is empty, deleting metadata")
                entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                return@forEach
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse(metadata.cacheUri)
                Timber.d("Enclosure URI: $uri")

                if (uri.toString().contains(context.packageName)) {
                    Timber.d("URI contains ${context.packageName}, deleting metadata")
                    entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                }

                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.IS_PENDING
                    ),
                    null,
                    null,
                    null,
                )

                if (cursor == null) {
                    Timber.d("Didn't find enclosure with URI: $uri")
                    entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                }

                cursor?.use {
                    if (!it.moveToFirst()) {
                        Timber.d("Didn't find enclosure with URI: $uri")
                        entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                        return@use
                    }

                    Timber.d("Found enclosure with URI: $uri")
                    Timber.d("Name: ${cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))}")

                    val pending =
                        cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING))
                    Timber.d("Pending: $pending")

                    if (pending == 1) {
                        Timber.d("Found pending enclosure, deleting metadata")
                        entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                    } else {
                        Timber.d("Enclosure is in sync with metadata")
                    }
                }
            } else {
                val file = File(metadata.cacheUri)

                if (file.exists() && metadata.downloadPercent != null && metadata.downloadPercent != 100L) {
                    file.delete()
                    entryEnclosureQueries.deleteWhere(entryId = metadata.entryId)
                }
            }
        }
    }

    private fun getExtension(mime: String): String {
        return when (mime) {
            "audio/mp3" -> "mp3"
            "audio/mpeg" -> "mp3"
            "audio/x-m4a" -> "m4a"
            "audio/opus" -> "opus"
            else -> mime.split("/").last()
        }
    }
}
