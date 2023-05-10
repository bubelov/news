package opengraph

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import co.appreactor.feedk.AtomLinkRel
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import conf.ConfRepo
import db.Db
import db.EntryWithoutContent
import http.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Single
class OpenGraphImagesRepo(
    private val confRepo: ConfRepo,
    private val db: Db,
) {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEntryImages() {
        withContext(Dispatchers.IO) {
            runCatching {
                consumePendingEntries(producePendingEntries())
            }.onFailure {
                delay(1000)
                fetchEntryImages()
            }
        }
    }

    private fun CoroutineScope.producePendingEntries(): ReceiveChannel<EntryWithoutContent> = produce {
        val sentEntryIds = mutableSetOf<String>()

        confRepo.conf
            .map { it.show_preview_images }
            .distinctUntilChanged()
            .collectLatest { showPreviewImages ->
                if (showPreviewImages) {
                    db.entryQueries.selectByOgImageChecked(false, BATCH_SIZE * 2L)
                        .asFlow()
                        .mapToList()
                        .collectLatest { highPrioEntries ->
                            highPrioEntries.forEach {
                                if (!sentEntryIds.contains(it.id)) {
                                    send(it)
                                    sentEntryIds += it.id
                                }
                            }
                        }
                }
            }
    }

    private fun CoroutineScope.consumePendingEntries(entries: ReceiveChannel<EntryWithoutContent>) {
        repeat(BATCH_SIZE) {
            launch {
                for (entry in entries) {
                    runCatching { fetchImage(entry) }
                        .onFailure { Log.e("opengraph", "Failed to fetch image", it) }
                }
            }
        }
    }

    private suspend fun fetchImage(entry: EntryWithoutContent) {
        withContext(Dispatchers.IO) {
            val link = entry.links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }
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

            val bitmap = runCatching {
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
                ext_og_image_url = imageUrl,
                ext_og_image_width = bitmap.width.toLong(),
                ext_og_image_height = bitmap.height.toLong(),
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