package entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import entries.EntriesRepository
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import common.ConfRepository
import db.EntryImage
import db.EntryImageQueries
import db.EntryImagesMetadata
import db.EntryImagesMetadataQueries
import db.EntryWithoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EntriesImagesRepository(
    private val imagesMetadataQueries: EntryImagesMetadataQueries,
    private val imageQueries: EntryImageQueries,
    private val entriesRepository: EntriesRepository,
    private val confRepository: ConfRepository,
) {

    companion object {
        const val MAX_WIDTH = 1080

        const val STATUS_PROCESSING = "processing"
        const val STATUS_PROCESSED = "processed"
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun selectAll() = imageQueries.selectAll().asFlow()

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
            return@withContext
        }

        imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSING))

        if (entry.link.isBlank()) {
            Timber.d("Link is blank, nothing to process")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
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
            it.log("Cannot fetch URL for feed item\nItem: ${entry.id} (${entry.title})\nURL: $link")
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

        val meta = Jsoup.parse(html).select("meta[property=\"og:image\"]").singleOrNull()

        var imageUrl = meta?.attr("content") ?: ""
        imageUrl = imageUrl.replace("http://", "https://")

        if (imageUrl.startsWith("//")) {
            imageUrl = imageUrl.replaceFirst("//", "https://")
        }

        if (imageUrl.isBlank()) {
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        val bitmap = kotlin.runCatching {
            Picasso.get().load(imageUrl).resize(MAX_WIDTH, 0).onlyScaleDown().get()
        }.getOrElse {
            it.log("Cannot download image by URL $imageUrl")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

        if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
            Timber.d("Image is corrupted")
            imagesMetadataQueries.insertOrReplace(metadata.copy(previewImageProcessingStatus = STATUS_PROCESSED))
            return@withContext
        }

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
        Timber.e(ImageProcessingException(message, this))
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