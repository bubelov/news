package co.appreactor.nextcloud.news.podcasts

import android.content.Context
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.FileOutputStream

class PodcastsManager(
    private val feedItemsRepository: FeedItemsRepository,
    private val context: Context
) {

    private val httpClient = OkHttpClient()

    suspend fun verifyCache() = withContext(Dispatchers.IO) {
        val news = feedItemsRepository.all().first()
        val podcasts = news.filter { it.isPodcast() }

        podcasts.forEach {
            val file = it.getPodcastFile(context)

            if (file.exists() && it.enclosureDownloadProgress != null && it.enclosureDownloadProgress != 100L) {
                file.delete()

                feedItemsRepository.updateEnclosureDownloadProgress(
                    id = it.id,
                    progress = null
                )
            }

            if (!file.exists() && it.enclosureDownloadProgress != null) {
                feedItemsRepository.updateEnclosureDownloadProgress(
                    id = it.id,
                    progress = null
                )
            }
        }
    }

    suspend fun downloadPodcast(id: Long) = withContext(Dispatchers.IO) {
        Timber.d("Downloading podcast $id")
        val podcast = feedItemsRepository.byId(id).first()!!
        Timber.d("Podcast title: ${podcast.title}")

        if (podcast.enclosureLink.isNullOrBlank()) {
            Timber.w("Enclosure link is null or blank")
            return@withContext
        }

        val file = podcast.getPodcastFile(context)

        if (file.exists()) {
            Timber.d("Already downloaded!")
            return@withContext
        }

        feedItemsRepository.updateEnclosureDownloadProgress(
            id = podcast.id,
            progress = 0L
        )

        Timber.d("Requesting URL ${podcast.enclosureLink}")

        val request = Request.Builder()
            .url(podcast.enclosureLink)
            .build()

        val response = httpClient.newCall(request).execute()

        if (response.isSuccessful) {
            Timber.d("Connected")

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
                            Timber.d("Progress: $progressPercent%")

                            feedItemsRepository.updateEnclosureDownloadProgress(
                                id = podcast.id,
                                progress = progressPercent
                            )

                            lastReportedProgressPercent = progressPercent
                        }
                    }
                }

                Timber.d("Downloaded")

                feedItemsRepository.updateEnclosureDownloadProgress(
                    id = podcast.id,
                    progress = 100
                )
            } catch (e: Exception) {
                Timber.e(e)

                feedItemsRepository.updateEnclosureDownloadProgress(
                    id = podcast.id,
                    -1L
                )

                output.flush()
                output.close()
                input.close()
            }
        } else {
            Timber.d("Failed to connect")
        }
    }
}