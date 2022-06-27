package opengraph

import android.graphics.Bitmap
import android.graphics.Color
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import conf.ConfRepository
import db.Database
import db.EntryWithoutContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Single
class OpenGraphImagesRepository(
    private val db: Database,
    private val confRepo: ConfRepository,
) {

    companion object {
        const val MAX_WIDTH_PX = 1080
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEntryImages() {
        while (true) {
            runCatching {
                confRepo.load()
                    .map { it.showPreviewImages }
                    .distinctUntilChanged()
                    .collectLatest { showPreviewImages ->
                        if (showPreviewImages) {
                            db.entryQueries.selectByOgImageChecked(false, 1)
                                .asFlow()
                                .mapToOneOrNull()
                                .filterNotNull()
                                .collectLatest { fetchImage(it) }
                        }
                    }
            }

            delay(1000)
        }
    }

    private suspend fun fetchImage(entry: EntryWithoutContent) {
        withContext(Dispatchers.Default) {
            if (entry.ogImageChecked) {
                throw IllegalStateException("ogImageChecked = 1")
            }

            val link = entry.links.firstOrNull { it.rel == "alternate" && it.type == "text/html" }
                ?: entry.links.firstOrNull { it.rel == "alternate" }

            if (link == null) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val request = httpClient.newCall(Request.Builder().url(link.href).build())

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
                Picasso.get().load(imageUrl).resize(MAX_WIDTH_PX, 0).onlyScaleDown().get()
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