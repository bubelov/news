package podcasts

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import db.EntryEnclosure
import db.EntryEnclosureQueries
import entries.EntriesRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File

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

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun download(entryId: String) = withContext(Dispatchers.IO) {
        val entry = entriesRepository.get(entryId).first()

        if (entry == null) {
            db.deleteByEntryId(entryId)
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
            downloadPercent = null,
        )

        db.insertOrReplace(enclosure)

        val file = context.getCachedPodcast(entryId, entry.enclosureLink)

        if (file.exists()) {
            file.delete()
        }

        db.insertOrReplace(enclosure.copy(downloadPercent = 0))

        val request = Request.Builder()
            .url(entry.enclosureLink)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            db.deleteByEntryId(entryId)
            return@withContext
        }

        val responseBody = response.body

        if (responseBody == null) {
            db.deleteByEntryId(entryId)
            return@withContext
        }

        runCatching {
            val bytesInBody = responseBody.contentLength()

            responseBody.source().use { bufferedSource ->
                file.sink().buffer().use { bufferedSink ->
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
                                db.insertOrReplace(enclosure.copy(downloadPercent = downloadedPercent))
                                lastReportedDownloadedPercent = downloadedPercent
                            }
                        }
                    }
                }
            }
        }.onSuccess {
            Timber.d("Podcast downloaded")
            db.insertOrReplace(enclosure.copy(downloadPercent = 100))

            Timber.d("Notifying media scanner")
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { path, uri -> Timber.d("Scan completed: $path $uri") }
        }.onFailure {
            db.deleteByEntryId(entryId)
            file.delete()
            throw it
        }
    }

    suspend fun deletePartialDownloads() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList().forEach { download ->
            val entry = entriesRepository.get(download.entryId).first()

            if (entry == null) {
                db.deleteByEntryId(download.entryId)
                return@forEach
            }

            val file = context.getCachedPodcast(entry.id, entry.enclosureLink)

            if (file.exists() && download.downloadPercent != null && download.downloadPercent != 100L) {
                file.delete()
                db.deleteByEntryId(download.entryId)
            }
        }
    }

    suspend fun deleteDownloadedPodcastsWithoutFiles() {
        db.selectAll().executeAsList().forEach {
            if (it.downloadPercent == 100L) {
                val entry = entriesRepository.get(it.entryId).first()

                if (entry == null) {
                    db.deleteByEntryId(it.entryId)
                    return@forEach
                }

                val file = context.getCachedPodcast(entry.id, entry.enclosureLink)

                if (!file.exists()) {
                    db.deleteByEntryId(it.entryId)
                }
            }
        }
    }
}

fun Context.getCachedPodcast(
    entryId: String,
    entryEnclosureLink: String,
): File {
    val fileName = "$entryId-${entryEnclosureLink.split("/").last().split("?").first()}"
    return File(getExternalFilesDir(Environment.DIRECTORY_PODCASTS), fileName)
}