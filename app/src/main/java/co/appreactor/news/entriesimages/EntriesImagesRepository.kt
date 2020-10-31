package co.appreactor.news.entriesimages

import co.appreactor.news.db.*
import co.appreactor.news.entries.EntriesRepository
import com.squareup.picasso.Picasso
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class EntriesImagesRepository(
    private val imagesMetadataQueries: EntryImagesMetadataQueries,
    private val imageQueries: EntryImageQueries,
    private val entriesRepository: EntriesRepository,
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
        val notOpenedEntries = entriesRepository.getNotOpened().first()

        notOpenedEntries.forEach { entry ->
            syncPreview(entry)
        }

        val openedEntries = entriesRepository.getOpened().first()

        openedEntries.forEach { entry ->
            syncPreview(entry)
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

        val metadata = imagesMetadataQueries.selectByEntryId(entry.id).executeAsOneOrNull() ?: EntryImagesMetadata(
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
        val imageUrl = meta?.attr("content")?.replace("http://", "https://")

        Timber.d("Got image URL: $imageUrl")

        if (imageUrl.isNullOrBlank()) {
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

            imagesMetadataQueries.insertOrReplace(metadata.copy(
                previewImageId = image.id,
                previewImageProcessingStatus = STATUS_PROCESSED,
            ))
        }
    }

    private fun Throwable.log(message: String) {
        Timber.e(EntryImageProcessingException(message, this))
    }
}