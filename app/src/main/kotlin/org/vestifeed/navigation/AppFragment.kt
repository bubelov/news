package org.vestifeed.navigation

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import org.vestifeed.parser.AtomLinkRel
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.vestifeed.app.db
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.http.await
import java.util.concurrent.TimeUnit

abstract class AppFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as Activity).binding.bottomNav.isVisible =
            parentFragmentManager.backStackEntryCount == 0 &&
                    (this is EntriesFragment || this is FeedsFragment)

        viewLifecycleOwner.lifecycleScope.launch {
            val httpClient = OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()

            while (true) {
                val imagesFetched = fetchEntryImages(httpClient, 1)

                if (imagesFetched.isNotEmpty()) {
                    onOpenGraphImageDownloaded()
                }

                delay(1000)
            }
        }
    }

    open fun onOpenGraphImageDownloaded() {}

    private suspend fun fetchEntryImages(
        httpClient: OkHttpClient,
        maxEntries: Long,
    ): List<EntryQueries.EntryWithoutContent> {
        val entries = withContext(Dispatchers.IO) {
            db().entry.selectByOgImageChecked(
                false,
                maxEntries,
            )
        }

        Log.d("opengraph", "fetched ${entries.size} entries with unchecked opengraph images")

        val successfulEntries = mutableListOf<EntryQueries.EntryWithoutContent>()

        for (entry in entries) {
            if (fetchEntryImage(httpClient, entry)) {
                successfulEntries += entry
            }
        }

        Log.d("opengraph", "fetched ${successfulEntries.size} opengraph images")

        return successfulEntries
    }

    private suspend fun fetchEntryImage(
        httpClient: OkHttpClient,
        entry: EntryQueries.EntryWithoutContent,
    ): Boolean {
        Log.d("opengraph", "trying to fetch opengraph image for post ${entry.title}")
        Log.d("opengraph", "links: ${entry.links}")

        val htmlLink =
            entry.links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }
                ?: entry.links.firstOrNull { it.rel is AtomLinkRel.Alternate }

        if (htmlLink == null) {
            Log.e("opengraph", "html link not found")
            withContext(Dispatchers.IO) {
                db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        Log.d("opengraph", "found html link: ${htmlLink.href}")

        val htmlLinkResponse = try {
            httpClient.newCall(Request.Builder().url(htmlLink.href).build()).await()
        } catch (e: Throwable) {
            Log.e("opengraph", "failed to fetch html page", e)
            withContext(Dispatchers.IO) {
                db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        if (!htmlLinkResponse.isSuccessful) {
            Log.e("opengraph", "http request failed with code ${htmlLinkResponse.code}")
            withContext(Dispatchers.IO) {
                db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val html = try {
            htmlLinkResponse.body.string()
        } catch (e: Throwable) {
            Log.e("opengraph", "failed to read response body", e)
            withContext(Dispatchers.IO) {
                db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val metas = Jsoup.parse(html).select("meta[property=\"og:image\"]")
        Log.d("opengraph", metas.toString())
        val imageUrl = metas.firstOrNull()?.attr("content") ?: ""

        if (imageUrl.isBlank()) {
            Log.e("opengraph", "cannot find opengraph image url")
            withContext(Dispatchers.IO) {
                db().entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        Log.d("opengraph", "found image url: $imageUrl")

        val imageRequest = ImageRequest.Builder(requireContext())
            .data(imageUrl)
            .size(800)
            .build()

        val bitmap = when (val imageResult = requireContext().imageLoader.execute(imageRequest)) {
            is SuccessResult -> {
                Log.d("opengraph", "bitmap load success")
                imageResult.image.toBitmap()
            }

            is ErrorResult -> {
                Log.d("opengraph", "bitmap load error")
                withContext(Dispatchers.IO) {
                    db().entry.updateOgImageChecked(true, entry.id)
                }
                return false
            }
        }

        db().entry.updateOgImage(
            extOgImageUrl = imageUrl,
            extOgImageWidth = bitmap.width.toLong(),
            extOgImageHeight = bitmap.height.toLong(),
            id = entry.id,
        )

        Log.d("opengraph", "successfully fetched opengraph image for post ${entry.title}")
        return true
    }
}