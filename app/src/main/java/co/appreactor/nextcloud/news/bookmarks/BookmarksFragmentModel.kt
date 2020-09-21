package co.appreactor.nextcloud.news.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.cropPreviewImages
import co.appreactor.nextcloud.news.common.showPreviewImages
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.EntryWithoutSummary
import co.appreactor.nextcloud.news.entries.*
import co.appreactor.nextcloud.news.entriesimages.EntriesImagesRepository
import co.appreactor.nextcloud.news.entriesenclosures.EntriesEnclosuresRepository
import co.appreactor.nextcloud.news.entriesenclosures.isAudioMime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
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

    private var syncPreviewsJob: Job? = null

    init {
        viewModelScope.launch {
            getShowPreviewImages().collect { show ->
                if (show) {
                    syncPreviews()
                }
            }
        }
    }

    suspend fun getBookmarks() = combine(
        feedsRepository.getAll(),
        entriesRepository.getBookmarked(),
        getShowPreviewImages(),
        getCropPreviewImages(),
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

    private suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    private suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    private suspend fun syncPreviews() {
        syncPreviewsJob?.cancel()

        syncPreviewsJob = viewModelScope.launch {
            runCatching {
                entriesImagesRepository.syncPreviews()
            }.onFailure {
                if (it is CancellationException) {
                    Timber.d("Sync previews cancelled")
                }
            }
        }
    }

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        showPreviewImages: Boolean,
        cropPreviewImages: Boolean,
    ): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                val publishedDateTime = LocalDateTime.parse(published)
                val publishedDateString = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(publishedDateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()))
                (feed?.title ?: "Unknown feed") + " · " + publishedDateString
            },
            viewed = false,
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
            supportingText = flow { emit(entriesSupportingTextRepository.getSupportingText(this@toRow.id)) },
            cachedSupportingText = entriesSupportingTextRepository.getCachedSupportingText(this.id)
        )
    }
}