package enclosures

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

@Single
class EnclosuresRepo(
    private val context: Context,
    private val db: Db,
) {

    private val httpClient = OkHttpClient()

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        if (enclosure.rel != "enclosure") {
            throw Exception("Invalid link rel: ${enclosure.rel}")
        }

        if (enclosure.type?.startsWith("audio") == false) {
            throw Exception("Invalid link type: ${enclosure.type}")
        }

        withContext(Dispatchers.Default) {
            val entry = db.entryQueries.selectById(enclosure.entryId!!).executeAsOne()

            db.entryQueries.updateLinks(
                id = entry.id,
                links = entry.links.map {
                    if (it.href == enclosure.href) {
                        it.copy(extEnclosureDownloadProgress = 0.0)
                    } else {
                        it
                    }
                }
            )

            val request = Request.Builder().url(enclosure.href).build()

            val response = runCatching {
                httpClient.newCall(request).execute()
            }.getOrElse {
                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            link.copy(extEnclosureDownloadProgress = null)
                        } else {
                            link
                        }
                    }
                )

                throw it
            }

            if (!response.isSuccessful) {
                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            link.copy(extEnclosureDownloadProgress = null)
                        } else {
                            link
                        }
                    }
                )

                throw Exception("Unexpected response code: ${response.code}")
            }

            var cacheUri: Uri? = null

            runCatching {
                val mediaType = enclosure.type!!.toMediaType()
                val fileExtension = mediaType.fileExtension()
                val fileName = "${UUID.randomUUID()}.$fileExtension"
                val outputStream: OutputStream

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, enclosure.type)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PODCASTS)
                            put(MediaStore.MediaColumns.IS_PENDING, true)
                        })

                    if (cacheUri == null) {
                        throw Exception("Failed to save enclosure to a media store")
                    }

                    outputStream = context.contentResolver.openOutputStream(cacheUri!!)!!
                } else {
                    val podcastsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
                    val file = File(podcastsDirectory, fileName)
                    cacheUri = Uri.fromFile(file)
                    outputStream = FileOutputStream(file)
                }

                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            link.copy(
                                extEnclosureDownloadProgress = 0.0,
                                extCacheUri = cacheUri.toString(),
                            )
                        } else {
                            link
                        }
                    }
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

                            delay(100)

                            downloadedBytes += buffer

                            if (downloadedBytes > 0) {
                                downloadedPercent =
                                    (downloadedBytes.toDouble() / bytesInBody.toDouble() * 100.0).toLong()

                                if (downloadedPercent > lastReportedDownloadedPercent) {
                                    db.entryQueries.updateLinks(
                                        id = entry.id,
                                        links = entry.links.map { link ->
                                            if (link.href == enclosure.href) {
                                                link.copy(
                                                    extEnclosureDownloadProgress = downloadedPercent.toDouble() / 100,
                                                    extCacheUri = cacheUri.toString(),
                                                )
                                            } else {
                                                link
                                            }
                                        }
                                    )

                                    lastReportedDownloadedPercent = downloadedPercent
                                }
                            }
                        }
                    }
                }
            }.onSuccess {
                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            link.copy(
                                extEnclosureDownloadProgress = 1.0,
                                extCacheUri = cacheUri.toString(),
                            )
                        } else {
                            link
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri?.let {
                        context.contentResolver.update(
                            it, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, false) }, null, null
                        )
                    }
                }
            }.onFailure {
                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            link.copy(
                                extEnclosureDownloadProgress = null,
                                extCacheUri = null,
                            )
                        } else {
                            link
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri?.let { uri ->
                        context.contentResolver.delete(uri, null, null)
                    }
                }

                throw it
            }
        }
    }

    suspend fun deletePartialDownloads() {
        Log.d(TAG, "Deleting partial downloads")
        val links = db.entryQueries.selectLinks().asFlow().mapToList().first().flatten()
        Log.d(TAG, "Got ${links.size} links")
        val enclosures = links.filter { it.rel == "enclosure" }
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
            context.contentResolver.delete(enclosure.extCacheUri.toUri(), null)
        }

        if (rowsDeleted != 1) {
            TODO()
        }

        update(
            enclosure.copy(
                extCacheUri = null,
                extEnclosureDownloadProgress = null,
            )
        )
    }

    private suspend fun update(enclosure: Link) {
        if (enclosure.feedId != null) {
            TODO()
        }

        if (enclosure.entryId != null) {
            val entry = db.entryQueries.selectById(enclosure.entryId).asFlow().mapToOneOrNull().first() ?: TODO()

            withContext(Dispatchers.Default) {
                db.entryQueries.updateLinks(
                    id = entry.id,
                    links = entry.links.map { link ->
                        if (link.href == enclosure.href) {
                            enclosure
                        } else {
                            link
                        }
                    }
                )
            }
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

    companion object {
        private const val TAG = "enclosures"
    }
}