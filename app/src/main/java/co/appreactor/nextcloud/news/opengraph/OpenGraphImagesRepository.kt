package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.db.OpenGraphImage
import co.appreactor.nextcloud.news.db.OpenGraphImageQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OpenGraphImagesRepository(
    private val db: OpenGraphImageQueries
) {

    companion object {
        private const val STATUS_PROCESSING = "processing"
        private const val STATUS_PROCESSED = "processed"
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getImageUrl(feedItem: FeedItem) = withContext(Dispatchers.IO) {
        parse(feedItem)
        db.selectByFeedItemId(feedItem.id).asFlow().map { it.executeAsOneOrNull()?.url ?: "" }
    }

    private suspend fun parse(feedItem: FeedItem) {
        return withContext(Dispatchers.IO) {
            val existingImage = db.selectByFeedItemId(feedItem.id).executeAsOneOrNull()

            if (existingImage != null) {
                return@withContext
            }

            val image = OpenGraphImage(
                feedItemId = feedItem.id,
                status = STATUS_PROCESSING,
                url = ""
            )

            db.insertOrReplace(image)

            if (feedItem.url.isNullOrBlank() || feedItem.url.startsWith("http://")) {
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            Timber.d("Processing ${feedItem.url}")

            val request = httpClient.newCall(Request.Builder().url(feedItem.url).build())

            val response = runCatching {
                request.execute()
            }.getOrElse { e ->
                e.log("Cannot fetch url for feed item ${feedItem.id} (${feedItem.title})")
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            if (!response.isSuccessful) {
                return@withContext
            }

            val html = runCatching {
                response.body!!.string()
            }.getOrElse { e ->
                e.log("Cannot fetch response body for feed item ${feedItem.id} (${feedItem.title})")
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()
            val imageUrl = meta?.attr("content")?.replace("http://", "https://")

            if (imageUrl.isNullOrBlank()) {
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            db.insertOrReplace(
                image.copy(
                    status = STATUS_PROCESSED,
                    url = imageUrl,
                )
            )
        }
    }

    private fun Throwable.log(message: String) {
        Timber.e(OpenGraphException(message, this))
    }
}