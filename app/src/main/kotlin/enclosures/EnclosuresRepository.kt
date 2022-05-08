package enclosures

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
import com.squareup.sqldelight.runtime.coroutines.mapToOneNotNull
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class EnclosuresRepository(
    private val entryEnclosureQueries: EntryEnclosureQueries,
    private val entriesRepo: EntriesRepository,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    fun getDownloadProgress(entryId: String): Flow<Long?> {
        return entryEnclosureQueries.selectByEntryId(entryId)
            .asFlow()
            .mapToOneNotNull()
            .map { it.downloadPercent }
    }

    fun selectByEntryId(entryId: String): Flow<EntryEnclosure?> {
        return entryEnclosureQueries.selectByEntryId(entryId)
            .asFlow()
            .mapToOneOrNull()
    }

    suspend fun download(entryId: String) {
        withContext(Dispatchers.Default) {
            val entry = entriesRepo.selectById(entryId).first()

            if (entry == null) {
                entryEnclosureQueries.deleteByEntryId(entryId)
                return@withContext
            }

            val firstEnclosureLink = entry.links.firstOrNull { it.rel == "enclosure" } ?: return@withContext

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
                .url(firstEnclosureLink.href)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                entryEnclosureQueries.deleteByEntryId(entryId)
                return@withContext
            }

            var cacheUri: Uri? = null

            runCatching {
                val mediaType = firstEnclosureLink.type.toMediaType()
                val fileExtension = mediaType.fileExtension()
                val fileName = "${java.util.UUID.randomUUID()}.$fileExtension"
                val outputStream: OutputStream

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, firstEnclosureLink.type)
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

                val responseBody = response.body!!
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
                entryEnclosureQueries.deleteByEntryId(entryId)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri?.let { uri ->
                        context.contentResolver.delete(uri, null, null)
                    }
                }

                throw it
            }
        }
    }

    suspend fun deleteIncompleteDownloads() {
        withContext(Dispatchers.Default) {
            entryEnclosureQueries.selectAll().executeAsList().forEach { metadata ->
                if (metadata.cacheUri.isEmpty()) {
                    entryEnclosureQueries.deleteByEntryId(metadata.entryId)
                    return@forEach
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = Uri.parse(metadata.cacheUri)

                    if (uri.toString().contains(context.packageName)) {
                        entryEnclosureQueries.deleteByEntryId(metadata.entryId)
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
                        entryEnclosureQueries.deleteByEntryId(metadata.entryId)
                    }

                    cursor?.use {
                        if (!it.moveToFirst()) {
                            entryEnclosureQueries.deleteByEntryId(metadata.entryId)
                            return@use
                        }

                        val isPendingIndex = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                        val pending = cursor.getInt(isPendingIndex)

                        if (pending == 1) {
                            entryEnclosureQueries.deleteByEntryId(metadata.entryId)
                        }
                    }
                } else {
                    val file = File(metadata.cacheUri)

                    if (file.exists() && metadata.downloadPercent != null && metadata.downloadPercent != 100L) {
                        file.delete()
                        entryEnclosureQueries.deleteByEntryId(metadata.entryId)
                    }
                }
            }
        }
    }

    suspend fun deleteFromCache(enclosure: EntryEnclosure) {
        withContext(Dispatchers.Default) {
            val file = File(enclosure.cacheUri)

            if (file.exists()) {
                file.delete()
            }

            entryEnclosureQueries.deleteByEntryId(enclosure.entryId)
        }
    }

    private fun MediaType.fileExtension(): String {
        return when (this.subtype) {
            "audio/mp3" -> "mp3"
            "audio/mpeg" -> "mp3"
            "audio/x-m4a" -> "m4a"
            "audio/opus" -> "opus"
            else -> this.subtype
        }
    }
}
