package org.vestifeed.opengraph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import coil3.request.ImageRequest
import co.appreactor.feedk.AtomLinkRel
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.toBitmap
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.Db
import org.vestifeed.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class OpenGraphImagesRepo(
    private val context: Context,
    private val confRepo: ConfRepo,
    private val db: Db,
) {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEntryImages() {
        Log.d("opengraph", "fetching org.vestifeed.entry images")
        withContext(Dispatchers.IO) {
            runCatching {
                fetchPendingEntries()
            }.onFailure {
                delay(1000)
                fetchEntryImages()
            }
        }
        Log.d("opengraph", "done fetching org.vestifeed.entry images")
    }

    private suspend fun fetchPendingEntries() {
        Log.d("opengraph", "fetching pending org.vestifeed.entries")
        val showPreviewImages = confRepo.conf.value.showPreviewImages
        if (!showPreviewImages) return

        val entries = db.entryQueries.selectByOgImageChecked(false, BATCH_SIZE * 2L)
        Log.d("opengraph", "fetched ${entries.size} pending org.vestifeed.entries")

        for (entry in entries) {
            runCatching { fetchImage(entry) }
                .onFailure { Log.e("opengraph", "Failed to fetch image", it) }
        }
    }

    private suspend fun fetchImage(entry: org.vestifeed.db.EntryWithoutContent) {
        withContext(Dispatchers.IO) {
            val link =
                entry.links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }
                    ?: entry.links.firstOrNull { it.rel is AtomLinkRel.Alternate }

            if (link == null) {
                db.entryQueries.updateOgImageChecked(true, entry.id)
                return@withContext
            }

            val response = runCatching {
                httpClient.newCall(Request.Builder().url(link.href).build()).await()
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

            val imageRequest = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            val bitmap = when (val imageResult = context.imageLoader.execute(imageRequest)) {
                is SuccessResult -> {
                    Log.d("opengraph", "bitmap load success")
                    imageResult.image.toBitmap()
                }

                is ErrorResult -> {
                    Log.d("opengraph", "bitmap load error")
                    db.entryQueries.updateOgImageChecked(true, entry.id)
                    return@withContext
                }
            }

//            if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
//                org.vestifeed.db.entryQueries.updateOgImageChecked(true, org.vestifeed.entry.id)
//                return@withContext
//            }

            db.entryQueries.updateOgImage(
                extOgImageUrl = imageUrl,
                extOgImageWidth = bitmap.width.toLong(),
                extOgImageHeight = bitmap.height.toLong(),
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

    companion object {
        const val MAX_WIDTH_PX = 1080
        const val BATCH_SIZE = 5
    }
}
