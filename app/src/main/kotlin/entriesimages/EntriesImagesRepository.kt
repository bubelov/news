package entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import common.ConfRepository
import db.Conf
import db.Database
import db.EntryWithoutContent
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Single
class EntriesImagesRepository(
    private val db: Database,
) {

    companion object {
        const val MAX_WIDTH = 1080
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun syncOpenGraphImages() {
        db.confQueries
            .select()
            .asFlow()
            .mapToOneOrDefault(ConfRepository.DEFAULT_CONF)
            .collectLatest { syncOpenGraphImages(it) }
    }

    private suspend fun syncOpenGraphImages(conf: Conf) {
        if (!conf.showPreviewImages) {
            return
        }

        db.entryQueries.selectByOgImageChecked(false, 1)
            .asFlow()
            .mapToOneOrNull()
            .filterNotNull()
            .collectLatest { syncPreview(it, db.linkQueries.selectByEntryid(it.id).executeAsList()) }
    }

    private suspend fun syncPreview(entry: EntryWithoutContent, links: List<Link>) {
        withContext(Dispatchers.Default) {
            if (entry.ogImageChecked) {
                throw IllegalStateException("ogImageChecked = 1")
            }

            val firstAltHtmlLink = links.firstOrNull { it.rel == "alternate" && it.type == "text/html" }

            if (firstAltHtmlLink == null) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val request = httpClient.newCall(Request.Builder().url(firstAltHtmlLink.href).build())

            val response = runCatching {
                request.execute()
            }.getOrElse {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            if (!response.isSuccessful) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val html = runCatching {
                response.body!!.string()
            }.getOrElse {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()

            var imageUrl = meta?.attr("content") ?: ""

            if (imageUrl.startsWith("//")) {
                imageUrl = imageUrl.replaceFirst("//", "https://")
            }

            if (imageUrl.isBlank()) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val bitmap = kotlin.runCatching {
                Picasso.get().load(imageUrl).resize(MAX_WIDTH, 0).onlyScaleDown().get()
            }.getOrElse {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            db.entryQueries.updateOgImage(
                ogImageUrl = imageUrl,
                ogImageWidth = bitmap.width.toLong(),
                ogImageHeight = bitmap.height.toLong(),
                id = entry.id,
            )
        }
    }

    private fun Bitmap.hasTransparentAngles(): Boolean {
        if (width == 0 || height == 0) {
            return false
        }

        if (getPixel(0, 0) == Color.TRANSPARENT) {
            return true
        }

        if (getPixel(width - 1, 0) == Color.TRANSPARENT) {
            return true
        }

        if (getPixel(0, height - 1) == Color.TRANSPARENT) {
            return true
        }

        if (getPixel(width - 1, height - 1) == Color.TRANSPARENT) {
            return true
        }

        return false
    }

    private fun Bitmap.looksLikeSingleColor(): Boolean {
        if (width == 0 || height == 0) {
            return false
        }

        val randomPixels = (1..100).map {
            getPixel(Random.nextInt(0, width), Random.nextInt(0, height))
        }

        return randomPixels.all { it == randomPixels.first() }
    }
}