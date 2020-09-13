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
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.FileOutputStream

class PodcastDownloadsRepository(
    private val db: PodcastDownloadQueries,
    private val feedItemsRepository: FeedItemsRepository,
    private val context: Context,
) {

    private val httpClient = OkHttpClient()

    suspend fun getDownloadProgress(feedItemId: Long) = withContext(Dispatchers.IO) {
        db.selectByFeedItemId(feedItemId).asFlow().map { it.executeAsOneOrNull()?.downloadPercent }
    }

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

        if (response.isSuccessful) {
            val input = BufferedInputStream(response.body!!.byteStream())
            val output = FileOutputStream(file)

            try {
                val data = ByteArray(1024)
                val total = response.body!!.contentLength()
                var downloaded = 0L
                var progressPercent: Long
                var lastReportedProgressPercent = 0L
                var count: Int

                while (true) {
                    count = input.read(data)

                    if (count == -1) {
                        break
                    }

                    downloaded += count
                    output.write(data, 0, count)

                    if (downloaded > 0) {
                        progressPercent = (downloaded.toDouble() / total.toDouble() * 100.0).toLong()

                        if (progressPercent > lastReportedProgressPercent) {
                            db.insertOrReplace(podcast.copy(downloadPercent = progressPercent))
                            lastReportedProgressPercent = progressPercent
                        }
                    }
                }

                db.insertOrReplace(podcast.copy(downloadPercent = 100))
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                output.flush()
                output.close()
                input.close()
            }
        } else {
            db.deleteByFeedItemId(feedItemId)
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

            if (file.exists() && podcastDownload.downloadPercent != null) {
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