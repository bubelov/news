package enclosures

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import db.Database
import db.Link
import kotlinx.coroutines.Dispatchers
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
class AudioEnclosuresRepository(
    private val db: Database,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    suspend fun download(audioEnclosure: Link) {
        withContext(Dispatchers.Default) {
            db.linkQueries.updateEnclosureDownloadProgress(
                extEnclosureDownloadProgress = 0.0,
                href = audioEnclosure.href,
            )

            val request = Request.Builder().url(audioEnclosure.href).build()

            val response = runCatching {
                httpClient.newCall(request).execute()
            }.getOrElse {
                db.linkQueries.updateEnclosureDownloadProgress(
                    extEnclosureDownloadProgress = null,
                    href = audioEnclosure.href,
                )

                throw it
            }

            if (!response.isSuccessful) {
                db.linkQueries.updateEnclosureDownloadProgress(
                    extEnclosureDownloadProgress = null,
                    href = audioEnclosure.href,
                )

                throw Exception("Unexpected response code: ${response.code}")
            }

            var cacheUri: Uri? = null

            runCatching {
                val mediaType = audioEnclosure.type!!.toMediaType()
                val fileExtension = mediaType.fileExtension()
                val fileName = "${UUID.randomUUID()}.$fileExtension"
                val outputStream: OutputStream

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, audioEnclosure.type)
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

                db.linkQueries.updateCacheUri(
                    extCacheUri = cacheUri.toString(),
                    feedId = audioEnclosure.feedId,
                    entryId = audioEnclosure.entryId,
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
                                    db.linkQueries.updateEnclosureDownloadProgress(
                                        extEnclosureDownloadProgress = downloadedPercent.toDouble() / 100,
                                        href = audioEnclosure.href,
                                    )

                                    lastReportedDownloadedPercent = downloadedPercent
                                }
                            }
                        }
                    }
                }
            }.onSuccess {
                db.linkQueries.insertOrReplace(
                    audioEnclosure.copy(
                        extEnclosureDownloadProgress = 1.0,
                        extCacheUri = cacheUri.toString(),
                    )
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cacheUri?.let {
                        context.contentResolver.update(
                            it, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, false) }, null, null
                        )
                    }
                }
            }.onFailure {
                db.linkQueries.transaction {
                    db.linkQueries.updateEnclosureDownloadProgress(
                        extEnclosureDownloadProgress = null,
                        href = audioEnclosure.href,
                    )

                    db.linkQueries.updateCacheUri(
                        extCacheUri = null,
                        feedId = audioEnclosure.feedId,
                        entryId = audioEnclosure.entryId,
                    )
                }

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
            db.linkQueries
                .selectByTypeAndDownloadInProgress(type = "enclosure")
                .executeAsList()
                .forEach { audioEnclosure ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val uri = Uri.parse(audioEnclosure.extCacheUri)

                        if (uri.toString().contains(context.packageName)) {
                            db.linkQueries.transaction {
                                db.linkQueries.updateEnclosureDownloadProgress(
                                    extEnclosureDownloadProgress = null,
                                    href = audioEnclosure.href,
                                )

                                db.linkQueries.updateCacheUri(
                                    extCacheUri = null,
                                    feedId = audioEnclosure.feedId,
                                    entryId = audioEnclosure.entryId,
                                )
                            }
                        }

                        val cursor = context.contentResolver.query(
                            uri,
                            arrayOf(
                                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.IS_PENDING
                            ),
                            null,
                            null,
                            null,
                        )

                        if (cursor == null) {
                            db.linkQueries.transaction {
                                db.linkQueries.updateEnclosureDownloadProgress(
                                    extEnclosureDownloadProgress = null,
                                    href = audioEnclosure.href,
                                )

                                db.linkQueries.updateCacheUri(
                                    extCacheUri = null,
                                    feedId = audioEnclosure.feedId,
                                    entryId = audioEnclosure.entryId,
                                )
                            }
                        }

                        cursor?.use {
                            if (!it.moveToFirst()) {
                                db.linkQueries.transaction {
                                    db.linkQueries.updateEnclosureDownloadProgress(
                                        extEnclosureDownloadProgress = null,
                                        href = audioEnclosure.href,
                                    )

                                    db.linkQueries.updateCacheUri(
                                        extCacheUri = null,
                                        feedId = audioEnclosure.feedId,
                                        entryId = audioEnclosure.entryId,
                                    )
                                }

                                return@use
                            }

                            val isPendingIndex = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                            val pending = cursor.getInt(isPendingIndex)

                            if (pending == 1) {
                                db.linkQueries.transaction {
                                    db.linkQueries.updateEnclosureDownloadProgress(
                                        extEnclosureDownloadProgress = null,
                                        href = audioEnclosure.href,
                                    )

                                    db.linkQueries.updateCacheUri(
                                        extCacheUri = null,
                                        feedId = audioEnclosure.feedId,
                                        entryId = audioEnclosure.entryId,
                                    )
                                }
                            }
                        }
                    } else {
                        val file = File(audioEnclosure.extCacheUri!!)

                        if (file.exists()) {
                            file.delete()
                        }

                        db.linkQueries.transaction {
                            db.linkQueries.updateEnclosureDownloadProgress(
                                extEnclosureDownloadProgress = null,
                                href = audioEnclosure.href,
                            )

                            db.linkQueries.updateCacheUri(
                                extCacheUri = null,
                                feedId = audioEnclosure.feedId,
                                entryId = audioEnclosure.entryId,
                            )
                        }
                    }
                }
        }
    }

    suspend fun deleteFromCache(audioEnclosure: Link) {
        withContext(Dispatchers.Default) {
            val file = File(audioEnclosure.extCacheUri!!)

            if (file.exists()) {
                file.delete()
            }

            db.linkQueries.transaction {
                db.linkQueries.updateEnclosureDownloadProgress(
                    extEnclosureDownloadProgress = null,
                    href = audioEnclosure.href,
                )

                db.linkQueries.updateCacheUri(
                    extCacheUri = null,
                    feedId = audioEnclosure.feedId,
                    entryId = audioEnclosure.entryId,
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
}
