package entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import entries.EntriesRepository
import com.squareup.picasso.Picasso
import common.ConfRepository
import db.EntryWithoutContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EntriesImagesRepository(
    private val entriesRepo: EntriesRepository,
    private val confRepo: ConfRepository,
) {

    companion object {
        const val MAX_WIDTH = 1080
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun syncPreviews() {
        withContext(Dispatchers.Default) {
            confRepo.select().collectLatest { prefs ->
                if (!prefs.showPreviewImages) {
                    return@collectLatest
                }

                entriesRepo.selectByReadOrBookmarked(
                    read = false,
                    bookmarked = true,
                ).collectLatest { entries ->
                    entries.chunked(10).forEach {
                        it.map { async { syncPreview(it) } }.awaitAll()
                    }
                }
            }
        }
    }

    private suspend fun syncPreview(entry: EntryWithoutContent) {
        withContext(Dispatchers.Default) {
            if (entry.ogImageChecked) {
                return@withContext
            }

            if (entry.link.isBlank()) {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            val link = if (entry.link.startsWith("http:")) {
                entry.link.replaceFirst("http:", "https:")
            } else {
                entry.link
            }

            val request = httpClient.newCall(Request.Builder().url(link).build())

            val response = runCatching {
                request.execute()
            }.getOrElse {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            if (!response.isSuccessful) {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            val html = runCatching {
                response.body!!.string()
            }.getOrElse {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()

            var imageUrl = meta?.attr("content") ?: ""
            imageUrl = imageUrl.replace("http://", "https://")

            if (imageUrl.startsWith("//")) {
                imageUrl = imageUrl.replaceFirst("//", "https://")
            }

            if (imageUrl.isBlank()) {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            val bitmap = kotlin.runCatching {
                Picasso.get().load(imageUrl).resize(MAX_WIDTH, 0).onlyScaleDown().get()
            }.getOrElse {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
                entriesRepo.setOgImageChecked(entry.id, true)
                return@withContext
            }

            entriesRepo.setOgImage(imageUrl, bitmap.width.toLong(), bitmap.height.toLong(), entry.id)
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