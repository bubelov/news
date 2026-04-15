package org.vestifeed.og

import android.content.Context
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.http.await
import org.vestifeed.parser.AtomLinkRel
import org.vestifeed.sync.Sync
import java.util.concurrent.TimeUnit

class OpenGraphImageFetcher(private val ctx: Context) {
    val lastDownload = MutableStateFlow<EntryQueries.EntryWithoutContent?>(null)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAndWatch() {
        fetchAll()

        ctx.sync().state.collectLatest {
            if (it is Sync.State.Idle) {
                fetchAll()
            }
        }
    }

    private suspend fun fetchAll() {
        while (true) {
            val uncheckedEntries = withContext(Dispatchers.IO) {
                ctx.db().entry.selectByOgImageChecked(
                    extOgImageChecked = false,
                    limit = 1,
                )
            }

            if (uncheckedEntries.isEmpty()) {
                return
            } else {
                if (fetchEntryImages(uncheckedEntries).isNotEmpty()) {
                    lastDownload.update { uncheckedEntries.first() }
                }
            }
        }
    }

    private suspend fun fetchEntryImages(entries: List<EntryQueries.EntryWithoutContent>): List<EntryQueries.EntryWithoutContent> {
        if (entries.isEmpty()) {
            return emptyList()
        }

        val successfulEntries = mutableListOf<EntryQueries.EntryWithoutContent>()

        for (entry in entries) {
            if (fetchEntryImage(entry)) {
                successfulEntries += entry
            }
        }

        return successfulEntries
    }

    private suspend fun fetchEntryImage(entry: EntryQueries.EntryWithoutContent): Boolean {
        val links = withContext(Dispatchers.IO) {
            ctx.db().link.selectByEntryId(entry.id)
        }
        val htmlLink =
            links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }
                ?: links.firstOrNull { it.rel is AtomLinkRel.Alternate }
        if (htmlLink == null) {
            withContext(Dispatchers.IO) {
                ctx.db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val htmlLinkResponse = try {
            httpClient.newCall(Request.Builder().url(htmlLink.href).build()).await()
        } catch (_: Throwable) {
            withContext(Dispatchers.IO) {
                ctx.db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        if (!htmlLinkResponse.isSuccessful) {
            withContext(Dispatchers.IO) {
                ctx.db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val html = try {
            htmlLinkResponse.body.string()
        } catch (_: Throwable) {
            withContext(Dispatchers.IO) {
                ctx.db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val metas = Jsoup.parse(html).select("meta[property=\"og:image\"]")
        val imageUrl = metas.firstOrNull()?.attr("content") ?: ""

        if (imageUrl.isBlank()) {
            withContext(Dispatchers.IO) {
                ctx.db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val imageRequest = ImageRequest.Builder(ctx)
            .data(imageUrl)
            .size(800)
            .build()

        val bitmap = when (val imageResult = ctx.imageLoader.execute(imageRequest)) {
            is SuccessResult -> {
                imageResult.image.toBitmap()
            }

            is ErrorResult -> {
                withContext(Dispatchers.IO) {
                    ctx.db().entry.updateOgImageChecked(true, entry.id)
                }
                return false
            }
        }

        ctx.db().entry.updateOgImage(
            extOgImageUrl = imageUrl,
            extOgImageWidth = bitmap.width.toLong(),
            extOgImageHeight = bitmap.height.toLong(),
            id = entry.id,
        )

        return true
    }
}