package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.db.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OpenGraphImagesRepository {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getImageUrl(feedItem: FeedItem): String {
        return withContext(Dispatchers.IO) {
            if (feedItem.url.isNullOrBlank() || feedItem.url.startsWith("http://")) {
                return@withContext ""
            }

            val request = httpClient.newCall(Request.Builder().url(feedItem.url).build())

            val response = runCatching {
                request.execute()
            }.getOrElse { e ->
                e.log("Cannot fetch url for feed item ${feedItem.id} (${feedItem.title})")
                return@withContext ""
            }

            if (!response.isSuccessful) {
                return@withContext ""
            }

            val html = runCatching {
                response.body!!.string()
            }.getOrElse { e ->
                e.log("Cannot fetch response body for feed item ${feedItem.id} (${feedItem.title})")
                return@withContext ""
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()
            val imageUrl = meta?.attr("content")?.replace("http://", "https://")

            if (imageUrl.isNullOrBlank()) {
                return@withContext ""
            }

            imageUrl
        }
    }

    private fun Throwable.log(message: String) {
        Timber.e(OpenGraphException(message, this))
    }
}