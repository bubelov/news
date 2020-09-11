package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OpenGraphImagesManager(
    private val feedItemsRepository: FeedItemsRepository
) {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchImages() = withContext(Dispatchers.IO) {
        feedItemsRepository.all().conflate().collect { feedItems ->
            feedItems
                .sortedByDescending { it.pubDate }
                .chunked(10)
                .forEach {
                    it.map { async { fetchOpenGraphImage(it) } }.awaitAll()
                }
        }
    }

    private suspend fun fetchOpenGraphImage(feedItem: FeedItem) = withContext(Dispatchers.IO) {
        if (feedItem.openGraphImageUrl.isNotBlank()
            || feedItem.openGraphImageParsingFailed
            || feedItem.url.startsWith("http://")
        ) {
            return@withContext
        }

        val request = httpClient.newCall(Request.Builder().url(feedItem.url).build())

        val response = runCatching {
            request.execute()
        }.getOrElse {
            Timber.e(
                OpenGraphException(
                    message = "Cannot fetch url for feed item ${feedItem.id} (${feedItem.title})",
                    cause = it
                )
            )

            feedItemsRepository.updateOpenGraphImageParsingFailed(
                id = feedItem.id,
                failed = true
            )

            return@withContext
        }

        Timber.d("Response code: ${response.code}")

        if (response.isSuccessful) {
            val html = runCatching {
                response.body!!.string()
            }.getOrElse {
                Timber.e(
                    OpenGraphException(
                        message = "Cannot fetch response body for feed item ${feedItem.id} (${feedItem.title})",
                        cause = it
                    )
                )

                feedItemsRepository.updateOpenGraphImageParsingFailed(
                    id = feedItem.id,
                    failed = true
                )

                return@withContext
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()
            val imageUrl = meta?.attr("content")?.replace("http://", "https://")

            if (imageUrl.isNullOrBlank()) {
                feedItemsRepository.updateOpenGraphImageParsingFailed(
                    id = feedItem.id,
                    failed = true
                )

                return@withContext
            }

            runCatching {
                Picasso.get().load(imageUrl).get()!!
            }.onFailure {
                Timber.e(
                    OpenGraphException(
                        message = "Cannot fetch Open Graph image for feed item ${feedItem.id} (${feedItem.title}). Image url: $imageUrl",
                        cause = it
                    )
                )

                feedItemsRepository.updateOpenGraphImageParsingFailed(
                    id = feedItem.id,
                    failed = true
                )

                return@withContext
            }

            feedItemsRepository.updateOpenGraphImageUrl(
                id = feedItem.id,
                url = imageUrl
            )
        }
    }
}