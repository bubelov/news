package bookmarks

import androidx.lifecycle.ViewModel
import feeds.FeedsRepository
import common.Preferences
import db.Feed
import db.EntryWithoutSummary
import common.Preferences.Companion.CROP_PREVIEW_IMAGES
import common.Preferences.Companion.SHOW_PREVIEW_IMAGES
import entries.*
import entriesimages.EntriesImagesRepository
import entriesenclosures.EntriesEnclosuresRepository
import entriesenclosures.isAudioMime
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class BookmarksFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val entriesEnclosuresRepository: EntriesEnclosuresRepository,
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun getBookmarks() = combine(
        feedsRepository.getAll(),
        entriesRepository.getBookmarked(),
        prefs.getBoolean(SHOW_PREVIEW_IMAGES),
        prefs.getBoolean(CROP_PREVIEW_IMAGES),
    ) { feeds, entries, showPreviewImages, cropPreviewImages ->
        entries.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, showPreviewImages, cropPreviewImages)
        }
    }

    suspend fun downloadEnclosure(entryId: String) {
        entriesEnclosuresRepository.downloadEnclosure(entryId)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        showPreviewImages: Boolean,
        cropPreviewImages: Boolean,
    ): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                val instant = Instant.parse(published)
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                (feed?.title ?: "Unknown feed") + " · " + format.format(Date(instant.millis))
            },
            podcast = enclosureLinkType.isAudioMime(),
            podcastDownloadPercent = flow {
                entriesEnclosuresRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flow {
                entriesImagesRepository.getPreviewImage(this@toRow.id).collect {
                    emit(it)
                }
            },
            cachedImage = lazy {
                runBlocking {
                    entriesImagesRepository.getPreviewImage(this@toRow.id).first()
                }

            },
            showImage = showPreviewImages,
            cropImage = cropPreviewImages,
            supportingText = flow {
                emit(
                    entriesSupportingTextRepository.getSupportingText(
                        this@toRow.id,
                        feed
                    )
                )
            },
            cachedSupportingText = entriesSupportingTextRepository.getCachedSupportingText(this.id),
            opened = opened,
        )
    }
}