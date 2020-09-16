package co.appreactor.nextcloud.news.podcasts

import android.content.Context
import co.appreactor.nextcloud.news.db.PodcastDownload
import co.appreactor.nextcloud.news.db.PodcastDownloadQueries
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

class PodcastDownloadsRepository(
    private val db: PodcastDownloadQueries,
    private val feedItemsRepository: FeedItemsRepository,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    suspend fun getDownloadProgress(feedItemId: Long) = withContext(Dispatchers.IO) {
        db.selectByFeedItemId(feedItemId).asFlow().map {
            it.executeAsOneOrNull()?.downloadPercent
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun downloadPodcast(feedItemId: Long) = withContext(Dispatchers.IO) {
        val feedItem = feedItemsRepository.byId(feedItemId).first()

        if (feedItem == null) {
            db.deleteByFeedItemId(feedItemId)
            return@withContext
        }

        if (feedItem.enclosureLink.isBlank()) {
            return@withContext
        }

        val existingPodcast = db.selectByFeedItemId(feedItemId).executeAsOneOrNull()

        if (existingPodcast != null) {
            return@withContext
        }

        val podcast = PodcastDownload(
            feedItemId = feedItemId,
            downloadPercent = null,
        )

        db.insertOrReplace(podcast)

        val file = feedItem.getPodcastFile(context)

        if (file.exists()) {
            file.delete()
        }

        db.insertOrReplace(podcast.copy(downloadPercent = 0))

        val request = Request.Builder()
            .url(feedItem.enclosureLink)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            db.deleteByFeedItemId(feedItemId)
            return@withContext
        }

        val responseBody = response.body

        if (responseBody == null) {
            db.deleteByFeedItemId(feedItemId)
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
                                db.insertOrReplace(podcast.copy(downloadPercent = downloadedPercent))
                                lastReportedDownloadedPercent = downloadedPercent
                            }
                        }
                    }
                }
            }
        }.onSuccess {
            db.insertOrReplace(podcast.copy(downloadPercent = 100))
        }.onFailure {
            db.deleteByFeedItemId(feedItemId)
            file.delete()
            throw it
        }
    }

    suspend fun deletePartialDownloads() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList().forEach { podcastDownload ->
            val feedItem = feedItemsRepository.byId(podcastDownload.feedItemId).first()

            if (feedItem == null) {
                db.deleteByFeedItemId(podcastDownload.feedItemId)
                return@forEach
            }

            val file = feedItem.getPodcastFile(context)

            if (file.exists() && podcastDownload.downloadPercent != null && podcastDownload.downloadPercent != 100L) {
                file.delete()
                db.deleteByFeedItemId(podcastDownload.feedItemId)
            }
        }
    }

    suspend fun deleteCompletedDownloadsWithoutFiles() {
        db.selectAll().executeAsList().forEach {
            if (it.downloadPercent == 100L) {
                val feedItem = feedItemsRepository.byId(it.feedItemId).first()

                if (feedItem == null) {
                    db.deleteByFeedItemId(it.feedItemId)
                    return@forEach
                }

                val file = feedItem.getPodcastFile(context)

                if (!file.exists()) {
                    db.deleteByFeedItemId(it.feedItemId)
                }
            }
        }
    }
}