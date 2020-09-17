package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.db.OpenGraphImage
import co.appreactor.nextcloud.news.db.OpenGraphImageQueries
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit

class EntriesImagesRepository(
    private val db: OpenGraphImageQueries
) {

    companion object {
        const val STATUS_PROCESSING = "processing"
        const val STATUS_PROCESSED = "processed"
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun warmUpMemoryCache() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList().collect { images ->
            images.forEach {
                if (it.url.isNotBlank()) {
                    Picasso.get().load(it.url).resize(1080, 0).onlyScaleDown().fetch()
                }
            }
        }
    }

    suspend fun getImage(entry: Entry) = withContext(Dispatchers.IO) {
        db.selectByEntryId(entry.id).asFlow().mapToOneOrNull()
    }

    suspend fun parse(entry: Entry) {
        return withContext(Dispatchers.IO) {
            val existingImage = db.selectByEntryId(entry.id).executeAsOneOrNull()

            if (existingImage != null) {
                return@withContext
            }

            val image = OpenGraphImage(
                entryId = entry.id,
                status = STATUS_PROCESSING,
                url = "",
                width = 0,
                height = 0
            )

            db.insertOrReplace(image)

            if (entry.link.isBlank()) {
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            Timber.d("Processing ${entry.link}")

            val request = httpClient.newCall(Request.Builder().url(entry.link).build())

            val response = runCatching {
                request.execute()
            }.getOrElse {
                it.log("Cannot fetch url for feed item ${entry.id} (${entry.title})")
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            if (!response.isSuccessful) {
                Timber.d("Invalid response code ${response.code} for item ${entry.id} (${entry.title}")
                return@withContext
            }

            val html = runCatching {
                response.body!!.string()
            }.getOrElse {
                it.log("Cannot fetch response body for feed item ${entry.id} (${entry.title})")
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()
            val imageUrl = meta?.attr("content")?.replace("http://", "https://")

            if (imageUrl.isNullOrBlank()) {
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            val bitmap = kotlin.runCatching {
                Picasso.get().load(imageUrl).resize(1080, 0).onlyScaleDown().get()
            }.getOrElse {
                it.log("Cannot download image by url $image")
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
                db.insertOrReplace(image.copy(status = STATUS_PROCESSED))
                return@withContext
            }

            db.insertOrReplace(
                image.copy(
                    status = STATUS_PROCESSED,
                    url = imageUrl,
                    width = bitmap.width.toLong(),
                    height = bitmap.height.toLong()
                )
            )
        }
    }

    private fun Throwable.log(message: String) {
        Timber.e(OpenGraphException(message, this))
    }
}