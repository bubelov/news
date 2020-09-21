package co.appreactor.nextcloud.news.entriesenclosures

import android.content.Context
import co.appreactor.nextcloud.news.db.EntryEnclosure
import co.appreactor.nextcloud.news.db.EntryEnclosureQueries
import co.appreactor.nextcloud.news.entries.EntriesRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

class EntriesEnclosuresRepository(
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
    suspend fun downloadEnclosure(entryId: String) = withContext(Dispatchers.IO) {
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

        val file = context.getCachedEnclosure(entryId, entry.enclosureLink, entry.enclosureLinkType)

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
                            downloadedPercent = (downloadedBytes.toDouble() / bytesInBody.toDouble() * 100.0).toLong()

                            if (downloadedPercent > lastReportedDownloadedPercent) {
                                db.insertOrReplace(enclosure.copy(downloadPercent = downloadedPercent))
                                lastReportedDownloadedPercent = downloadedPercent
                            }
                        }
                    }
                }
            }
        }.onSuccess {
            db.insertOrReplace(enclosure.copy(downloadPercent = 100))
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

            val file = context.getCachedEnclosure(entry.id, entry.enclosureLink, entry.enclosureLinkType)

            if (file.exists() && download.downloadPercent != null && download.downloadPercent != 100L) {
                file.delete()
                db.deleteByEntryId(download.entryId)
            }
        }
    }

    suspend fun deleteDownloadedEnclosuresWithoutFiles() {
        db.selectAll().executeAsList().forEach {
            if (it.downloadPercent == 100L) {
                val entry = entriesRepository.get(it.entryId).first()

                if (entry == null) {
                    db.deleteByEntryId(it.entryId)
                    return@forEach
                }

                val file = context.getCachedEnclosure(entry.id, entry.enclosureLink, entry.enclosureLinkType)

                if (!file.exists()) {
                    db.deleteByEntryId(it.entryId)
                }
            }
        }
    }
}