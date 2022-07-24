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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

    private val queue = Channel<EntryWithoutContent>(5)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEntryImages() {
        withContext(Dispatchers.Default) {
            launch { restartOnFailure { fillQueue() } }
            launch { restartOnFailure { processQueue() } }
        }
    }

    private suspend fun fillQueue() {
        confRepo.conf
            .map { it.showPreviewImages }
            .distinctUntilChanged()
            .collectLatest { showPreviewImages ->
                if (showPreviewImages) {
                    db.entryQueries.selectByOgImageChecked(false, 5)
                        .asFlow()
                        .mapToList()
                        .collectLatest { highPrioEntries ->
                            highPrioEntries.forEach {
                                queue.send(it)
                            }
                        }
                }
            }
    }

    private suspend fun processQueue() {
        withContext(Dispatchers.Default) {
            repeat(5) {
                launch {
                    for (entry in queue) {
                        runCatching { fetchImage(entry) }
                            .onFailure { Log.e("opengraph", "Failed to fetch image", it) }
                    }
                }
            }
        }
    }

    private suspend fun fetchImage(entry: EntryWithoutContent) {
        withContext(Dispatchers.Default) {
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

    private suspend fun restartOnFailure(block: suspend () -> Unit) {
        runCatching {
            block()
        }.onFailure {
            delay(1000)
            restartOnFailure(block)
        }
    }

    companion object {
        const val MAX_WIDTH_PX = 1080
    }
}