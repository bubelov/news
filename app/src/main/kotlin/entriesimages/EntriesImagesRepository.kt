package entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import entries.EntriesRepository
import com.squareup.picasso.Picasso
import common.ConfRepository
import db.EntryWithoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EntriesImagesRepository(
    private val entriesRepository: EntriesRepository,
    private val confRepository: ConfRepository,
) {

    companion object {
        const val MAX_WIDTH = 1080
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    val lastOgImageUrl = MutableStateFlow("")

    suspend fun syncPreviews() = withContext(Dispatchers.IO) {
        confRepository.select().collectLatest { prefs ->
            if (!prefs.showPreviewImages) {
                return@collectLatest
            }

            entriesRepository.selectByReadOrBookmarked(
                read = false,
                bookmarked = true,
            ).collectLatest { entries ->
                entries.chunked(10).forEach {
                    it.map { async { syncPreview(it) } }.awaitAll()
                }
            }
        }
    }

    private suspend fun syncPreview(entry: EntryWithoutSummary) = withContext(Dispatchers.IO) {
        if (entry.ogImageChecked) {
            return@withContext
        }

        if (entry.link.isBlank()) {
            entriesRepository.setOgImageChecked(entry.id, true)
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
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        if (!response.isSuccessful) {
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        val html = runCatching {
            response.body!!.string()
        }.getOrElse {
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()

        var imageUrl = meta?.attr("content") ?: ""
        imageUrl = imageUrl.replace("http://", "https://")

        if (imageUrl.startsWith("//")) {
            imageUrl = imageUrl.replaceFirst("//", "https://")
        }

        if (imageUrl.isBlank()) {
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        val bitmap = kotlin.runCatching {
            Picasso.get().load(imageUrl).resize(MAX_WIDTH, 0).onlyScaleDown().get()
        }.getOrElse {
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
            entriesRepository.setOgImageChecked(entry.id, true)
            return@withContext
        }

        entriesRepository.setOgImageChecked(entry.id, true)
        entriesRepository.setOgImage(imageUrl, bitmap.width.toLong(), bitmap.height.toLong(), entry.id)
        lastOgImageUrl.update { imageUrl }
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