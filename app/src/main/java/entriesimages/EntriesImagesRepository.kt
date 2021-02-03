package entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import db.*
import entries.EntriesRepository
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import common.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EntriesImagesRepository(
    private val imagesMetadataQueries: EntryImagesMetadataQueries,
    private val imageQueries: EntryImageQueries,
    private val entriesRepository: EntriesRepository,
    private val prefs: Preferences,
) {

    companion object {
        const val MAX_WIDTH = 1080

        const val STATUS_PROCESSING = "processing"
        const val STATUS_PROCESSED = "processed"
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun syncPreviews() = withContext(Dispatchers.IO) {
        Timber.d("Sync daemon started")

        prefs.getBoolean(Preferences.SHOW_PREVIEW_IMAGES).collectLatest { showPreviewImages ->
            if (!showPreviewImages) {
                return@collectLatest
            }

            entriesRepository.getAll().collectLatest { entries ->
                Timber.d("Got ${entries.size} entries")
                val notOpenedEntries = entries.filterNot { it.opened }
                Timber.d("Not opened entries: ${notOpenedEntries.size}")
                val bookmarkedEntries = entries.filter { it.bookmarked }
                Timber.d("Bookmarked entries: ${bookmarkedEntries.size}")
                val otherEntries = entries.filter { it.opened && !it.bookmarked }
                Timber.d("Other entries: ${otherEntries.size}")

                val queue =
                    ((notOpenedEntries + bookmarkedEntries).sortedByDescending { it.published } + otherEntries)

                queue.chunked(10).forEach {
                    it.map { async { syncPreview(it) } }.awaitAll()
                }
            }
        }
    }

    suspend fun getPreviewImage(entryId: String) = withContext(Dispatchers.IO) {
        combine(
            imagesMetadataQueries.selectByEntryId(entryId).asFlow().mapToOneOrNull(),
            imageQueries.selectByEntryId(entryId).asFlow().mapToList(),
        ) { metadata, images ->
            if (metadata == null || metadata.previewImageProcessingStatus != STATUS_PROCESSED) {
                null
            } else {
                images.singleOrNull { it.id == metadata.previewImageId }
            }
        }
    }

    private suspend fun syncPreview(entry: EntryWithoutSummary) = withContext(Dispatchers.IO) {
        Timber.d("Syncing preview image for entry ${entry.id} ${entry.title}")

        val metadata = imagesMetadataQueries.selectByEntryId(entry.id).executeAsOneOrNull()
            ?: EntryImagesMetadata(
                entryId = entry.id,
                previewImageProcessingStatus = "",
                previewImageId = null,
                summaryImagesProcessingStatus = "",
            ).apply {
                imagesMetadataQueries.insertOrReplace(this)
            }

        if (metadata.previewImageProcessingStatus.isNotBlank()) {
            Timber.d("Preview image already processed or processing, nothing to do here")
            return@withContext
        }

        Timber.d("Starting processing")
        imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSING))

        if (entry.link.isBlank()) {
            Timber.d("Link is blank, nothing to process")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        Timber.d("Requesting ${entry.link}")
        val request = httpClient.newCall(Request.Builder().url(entry.link).build())

        val response = runCatching {
            request.execute()
        }.getOrElse {
            it.log("Cannot fetch url for feed item ${entry.id} (${entry.title})")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
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
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        Timber.d("Got HTML")

        val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()

        var imageUrl = meta?.attr("content") ?: ""
        imageUrl = imageUrl.replace("http://", "https://")

        if (imageUrl.startsWith("//")) {
            imageUrl = imageUrl.replaceFirst("//", "https://")
        }

        Timber.d("Got image URL: $imageUrl")

        if (imageUrl.isBlank()) {
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        val bitmap = kotlin.runCatching {
            Picasso.get().load(imageUrl).resize(MAX_WIDTH, 0).onlyScaleDown().get()
        }.getOrElse {
            it.log("Cannot download image by url $imageUrl")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
            Timber.d("Image is corrupted")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        Timber.d("All fine, saving preview image")

        imagesMetadataQueries.transaction {
            val image = EntryImage(
                id = UUID.randomUUID().toString(),
                entryId = entry.id,
                url = imageUrl,
                width = bitmap.width.toLong(),
                height = bitmap.height.toLong(),
            )

            imageQueries.insertOrReplace(image)

            imagesMetadataQueries.insertOrReplace(
                metadata.copy(
                    previewImageId = image.id,
                    previewImageProcessingStatus = STATUS_PROCESSED,
                )
            )
        }
    }

    private fun Throwable.log(message: String) {
        Timber.e(EntryImageProcessingException(message, this))
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