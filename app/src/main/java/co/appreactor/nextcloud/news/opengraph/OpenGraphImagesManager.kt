package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber

class OpenGraphImagesManager(
    private val feedItemsRepository: FeedItemsRepository
) {

    private val httpClient = OkHttpClient()

    suspend fun start() = withContext(Dispatchers.IO) {
        feedItemsRepository.all().collect { news ->
            Timber.d("Got ${news.size} news")
            val chunks = news.sortedByDescending { it.pubDate }.chunked(10)
            Timber.d("Chunks: ${chunks.size}")

            chunks.forEach {
                it.map { async { fetchOpenGraphImage(it) } }.awaitAll()
            }
        }
    }

    private suspend fun fetchOpenGraphImage(newsItem: FeedItem) = withContext(Dispatchers.IO) {
        if (newsItem.openGraphImageUrl.isNotBlank()
            || newsItem.openGraphImageParsingFailed
            || newsItem.url.startsWith("http://")
        ) {
            return@withContext
        }

        Timber.d("Processing item ${newsItem.id} (${newsItem.title})")
        Timber.d("URL: ${newsItem.url}")

        runCatching {
            val request = Request.Builder().url(newsItem.url).build()
            val response = httpClient.newCall(request).execute()
            Timber.d("Response code: ${response.code}")

            if (response.isSuccessful) {
                val html = response.body!!.string()
                val document = Jsoup.parse(html)

                val metas = document.select("meta[property=\"og:image\"]")

                if (metas.isNotEmpty()) {
                    val imageUrl = metas.first().attr("content")

                    if (imageUrl.isNotBlank()) {
                        Timber.d("Image URL: $imageUrl")
                        val image = Picasso.get().load(imageUrl).get()!!

                        Timber.d("Downloaded image. Resolution: ${image.width} x ${image.height}")

                        if (image.width > 480 && image.height.toDouble() > image.width.toDouble() / 2.5) {
                            feedItemsRepository.updateOpenGraphImageUrl(
                                id = newsItem.id,
                                url = imageUrl
                            )
                        } else {
                            Timber.d("Invalid image. Size: ${image.width} x ${image.height}")

                            feedItemsRepository.updateOpenGraphImageParsingFailed(
                                id = newsItem.id,
                                failed = true
                            )
                        }
                    } else {
                        Timber.w("Open Graph tag seems to be corrupted")

                        feedItemsRepository.updateOpenGraphImageParsingFailed(
                            id = newsItem.id,
                            failed = true
                        )
                    }
                } else {
                    Timber.d("This page has no Open Graph images")

                    feedItemsRepository.updateOpenGraphImageParsingFailed(
                        id = newsItem.id,
                        failed = true
                    )
                }
            }
        }.getOrElse {
            Timber.e(it)

            feedItemsRepository.updateOpenGraphImageParsingFailed(
                id = newsItem.id,
                failed = true
            )
        }
    }

}