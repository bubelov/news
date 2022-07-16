package enclosures

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import co.appreactor.feedk.AtomLinkRel
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.Entry
import db.Link
import http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.koin.core.annotation.Single
import java.util.UUID
import java.util.concurrent.TimeUnit

@Single
class EnclosuresRepo(
    private val context: Context,
    private val db: Db,
) {

    private val httpClient = OkHttpClient()

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        if (enclosure.rel !is AtomLinkRel.Enclosure) {
            throw Exception("Invalid link rel: ${enclosure.rel}")
        }

        if (enclosure.type?.startsWith("audio") == false) {
            throw Exception("Invalid link type: ${enclosure.type}")
        }

        val entry = db.entryQueries.selectById(enclosure.entryId!!).asFlow().mapToOneOrNull().first()
            ?: throw Exception("Entry ${enclosure.entryId} does not exist")

        updateLink(
            link = enclosure.copy(extEnclosureDownloadProgress = 0.0),
            entry = entry,
        )

        val request = Request.Builder().url(enclosure.href).build()

        val response = runCatching {
            httpClient.newCall(request).await()
        }.getOrElse {
            updateLink(
                link = enclosure.copy(extEnclosureDownloadProgress = null),
                entry = entry,
            )

            throw it
        }

        if (!response.isSuccessful) {
            updateLink(
                link = enclosure.copy(extEnclosureDownloadProgress = null),
                entry = entry,
            )

            throw Exception("Unexpected response code: ${response.code}")
        }

        val mediaType = enclosure.type!!.toMediaType()
        val fileExtension = mediaType.fileExtension()
        val fileName = "${UUID.randomUUID()}.$fileExtension"

        val cacheUri = kotlin.runCatching {
            withContext(Dispatchers.Default) {
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, enclosure.type)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PODCASTS)
                        put(MediaStore.MediaColumns.IS_PENDING, true)
                    })!!
            }
        }.getOrElse {
            throw it
        }

        runCatching {
            val outputStream = withContext(Dispatchers.Default) {
                context.contentResolver.openOutputStream(cacheUri)
                    ?: throw Exception("Failed to open an output stream for URI $cacheUri")
            }

            updateLink(
                link = enclosure.copy(
                    extEnclosureDownloadProgress = 0.0,
                    extCacheUri = cacheUri,
                ),
                entry = entry,
            )

            val responseBody = response.body!!
            val bytesInBody = responseBody.contentLength()

            responseBody.source().use { bufferedSource ->
                outputStream.sink().buffer().use { bufferedSink ->
                    var progressBytes = 0L
                    var progress: Double
                    var lastNotificationNanos = System.nanoTime()

                    while (true) {
                        val buffer = bufferedSource.read(bufferedSink.buffer, 1024L * 16L)
                        bufferedSink.flush()

                        if (buffer == -1L) {
                            break
                        }

                        progressBytes += buffer
                        progress = progressBytes.toDouble() / bytesInBody.toDouble()

                        if (System.nanoTime() - lastNotificationNanos > TimeUnit.MILLISECONDS.toNanos(100)) {
                            updateLink(
                                link = enclosure.copy(
                                    extEnclosureDownloadProgress = progress,
                                    extCacheUri = cacheUri,
                                ),
                                entry = entry,
                            )

                            lastNotificationNanos = System.nanoTime()
                        }
                    }
                }
            }
        }.onSuccess {
            updateLink(
                link = enclosure.copy(
                    extEnclosureDownloadProgress = 1.0,
                    extCacheUri = cacheUri,
                ),
                entry = entry,
            )

            context.contentResolver.update(
                cacheUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, false) },
                null,
                null,
            )
        }.onFailure { throwable ->
            updateLink(
                link = enclosure.copy(
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                ),
                entry = entry,
            )

            context.contentResolver.delete(cacheUri, null, null)

            throw throwable
        }
    }

    suspend fun deletePartialDownloads() {
        Log.d(TAG, "Deleting partial downloads")
        val links = db.entryQueries.selectLinks().asFlow().mapToList().first().flatten()
        Log.d(TAG, "Got ${links.size} links")
        val enclosures = links.filter { it.rel is AtomLinkRel.Enclosure }
        Log.d(TAG, "Of them, ${enclosures.size} are enclosures")

        val partialDownloads = enclosures.filter {
            it.extEnclosureDownloadProgress != null && it.extEnclosureDownloadProgress != 1.0
        }

        Log.d(TAG, "Number of partial downloads: ${partialDownloads.size}")
        partialDownloads.forEach { deleteFromCache(it) }
    }

    suspend fun deleteFromCache(enclosure: Link) {
        if (enclosure.extCacheUri == null) {
            TODO()
        }

        val rowsDeleted = withContext(Dispatchers.Default) {
            context.contentResolver.delete(enclosure.extCacheUri, null, null)
        }

        if (rowsDeleted != 1) {
            TODO()
        }

        updateLink(
            enclosure.copy(
                extCacheUri = null,
                extEnclosureDownloadProgress = null,
            )
        )
    }

    private suspend fun updateLink(link: Link) {
        if (link.feedId != null) {
            TODO()
        }

        if (link.entryId != null) {
            val entry = db.entryQueries.selectById(link.entryId).asFlow().mapToOneOrNull().first() ?: TODO()
            updateLink(link, entry)
        }
    }

    private suspend fun updateLink(link: Link, entry: Entry): Link {
        withContext(Dispatchers.Default) {
            db.entryQueries.updateLinks(
                id = entry.id,
                links = entry.links.map {
                    if (it.href == link.href) {
                        link
                    } else {
                        it
                    }
                },
            )
        }

        return link
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

    companion object {
        private const val TAG = "enclosures"
    }
}