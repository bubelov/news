package org.vestifeed.enclosures

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import org.vestifeed.parser.AtomLinkRel
import org.vestifeed.db.Database
import org.vestifeed.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.Link
import java.util.UUID
import java.util.concurrent.TimeUnit

class EnclosuresRepo(
    private val context: Context,
    private val db: Database,
) {

    private val httpClient = OkHttpClient()

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        if (enclosure.rel !is AtomLinkRel.Enclosure) {
            throw Exception("Invalid link rel: ${enclosure.rel}")
        }

        if (enclosure.type?.startsWith("audio") == false) {
            throw Exception("Invalid link type: ${enclosure.type}")
        }

        val entry = db.entry.selectById(enclosure.entryId!!)
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
            withContext(Dispatchers.IO) {
                context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
            val outputStream = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(cacheUri)
                    ?: throw Exception("Failed to open an output stream for URI $cacheUri")
            }

            updateLink(
                link = enclosure.copy(
                    extEnclosureDownloadProgress = 0.0,
                    extCacheUri = cacheUri.toString(),
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

                        if (System.nanoTime() - lastNotificationNanos > TimeUnit.MILLISECONDS.toNanos(
                                100
                            )
                        ) {
                            updateLink(
                                link = enclosure.copy(
                                    extEnclosureDownloadProgress = progress,
                                    extCacheUri = cacheUri.toString(),
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
                    extCacheUri = cacheUri.toString(),
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
        val links = db.entry.selectAllLinks().flatten()
        Log.d(TAG, "Got ${links.size} links")
        val enclosures = links.filter { it.rel is AtomLinkRel.Enclosure }
        Log.d(TAG, "Of them, ${enclosures.size} are org.vestifeed.enclosures")

        val partialDownloads = enclosures.filter {
            it.extEnclosureDownloadProgress != null && it.extEnclosureDownloadProgress != 1.0
        }

        Log.d(TAG, "Number of partial downloads: ${partialDownloads.size}")
        partialDownloads.forEach { enclosure -> deleteFromCache(enclosure) }
    }

    suspend fun deleteFromCache(enclosure: Link) {
        if (enclosure.extCacheUri == null) {
            updateLink(enclosure.copy(extEnclosureDownloadProgress = null))
            return
        }

        val rowsDeleted = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.delete(
                    enclosure.extCacheUri.toUri(),
                    null,
                    null,
                )
            }.getOrDefault(1)
        }

        if (rowsDeleted != 1) {
            throw Exception("Failed to delete cache org.vestifeed.entry")
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
            throw Exception("Cannot update link with feedId")
        }

        if (link.entryId != null) {
            val entry = db.entry.selectById(link.entryId) ?: throw Exception("Entry not found")
            updateLink(link, entry)
        }
    }

    private suspend fun updateLink(link: Link, entry: Entry): Link {
        withContext(Dispatchers.IO) {
            db.entry.updateLinks(
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
