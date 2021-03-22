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
import kotlinx.coroutines.flow.Flow
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
    private val db: EntryEnclosureQueries,
    private val entriesRepository: EntriesRepository,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    suspend fun getDownloadProgress(entryId: String) = withContext(Dispatchers.IO) {
        db.selectByEntryId(entryId).asFlow().map {
            it.executeAsOneOrNull()?.downloadPercent
        }
    }

    suspend fun getCachedPodcastUri(entryId: String): Flow<Uri> = withContext(Dispatchers.IO) {
        db.selectByEntryId(entryId).asFlow().map {
            Uri.parse(it.executeAsOneOrNull()?.cacheUri)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun download(entryId: String) = withContext(Dispatchers.IO) {
        val entry = entriesRepository.selectById(entryId)

        if (entry == null) {
            db.deleteWhere(entryId = entryId)
            return@withContext
        }

        if (entry.enclosureLink.isBlank()) {
            return@withContext
        }

        val existingEnclosure = db.selectByEntryId(entryId).executeAsOneOrNull()

        if (existingEnclosure != null) {
            return@withContext
        }

        val enclosure = EntryEnclosure(
            entryId = entryId,
            downloadPercent = 0,
            cacheUri = "",
        )

        db.insertOrReplace(enclosure)

        val request = Request.Builder()
            .url(entry.enclosureLink)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            db.deleteWhere(entryId = entryId)
            return@withContext
        }

        val responseBody = response.body

        if (responseBody == null) {
            db.deleteWhere(entryId = entryId)
            return@withContext
        }

        var cacheUri: Uri? = null

        runCatching {
            val fileName = "${entry.title}.${getExtension(entry.enclosureLinkType)}"
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

                outputStream = context.contentResolver.openOutputStream(cacheUri!!)!!
            } else {
                val podcastsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
                val file = File(podcastsDirectory, fileName)
                cacheUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            db.setCacheUri(
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
                        val buffer = bufferedSource.read(bufferedSink.buffer, 1024 * 16)

                        if (buffer == -1L) {
                            break
                        }

                        downloadedBytes += buffer

                        if (downloadedBytes > 0) {
                            downloadedPercent =
                                (downloadedBytes.toDouble() / bytesInBody.toDouble() * 100.0).toLong()

                            if (downloadedPercent > lastReportedDownloadedPercent) {
                                db.setDowloadPercent(
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
            db.setDowloadPercent(
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
            db.deleteWhere(entryId = entryId)

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

        db.selectAll().executeAsList().forEach { metadata ->
            if (metadata.cacheUri.isEmpty()) {
                Timber.d("Cache URI is empty, deleting metadata")
                db.deleteWhere(entryId = metadata.entryId)
                return@forEach
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse(metadata.cacheUri)
                Timber.d("Enclosure URI: $uri")

                if (uri.toString().contains(context.packageName)) {
                    Timber.d("URI contains ${context.packageName}, deleting metadata")
                    db.deleteWhere(entryId = metadata.entryId)
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
                    db.deleteWhere(entryId = metadata.entryId)
                }

                cursor?.use {
                    if (!it.moveToFirst()) {
                        Timber.d("Didn't find enclosure with URI: $uri")
                        db.deleteWhere(entryId = metadata.entryId)
                        return@use
                    }

                    Timber.d("Found enclosure with URI: $uri")
                    Timber.d("Name: ${cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))}")

                    val pending =
                        cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING))
                    Timber.d("Pending: $pending")

                    if (pending == 1) {
                        Timber.d("Found pending enclosure, deleting metadata")
                        db.deleteWhere(entryId = metadata.entryId)
                    } else {
                        Timber.d("Enclosure is in sync with metadata")
                    }
                }
            } else {
                val file = File(metadata.cacheUri)

                if (file.exists() && metadata.downloadPercent != null && metadata.downloadPercent != 100L) {
                    file.delete()
                    db.deleteWhere(entryId = metadata.entryId)
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
