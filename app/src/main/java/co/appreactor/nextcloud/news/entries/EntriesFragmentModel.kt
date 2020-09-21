package co.appreactor.nextcloud.news.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.common.*
import co.appreactor.nextcloud.news.db.EntryWithoutSummary
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.entriesimages.EntriesImagesRepository
import co.appreactor.nextcloud.news.podcasts.EntriesAudioRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import java.text.DateFormat
import java.util.*

class EntriesFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val entriesAudioRepository: EntriesAudioRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    private var syncPreviewsJob: Job? = null

    init {
        viewModelScope.launch {
            entriesAudioRepository.deleteCompletedDownloadsWithoutFiles()
            entriesAudioRepository.deletePartialDownloads()
        }

        viewModelScope.launch {
            getShowPreviewImages().collect { show ->
                if (show) {
                    syncPreviews()
                }
            }
        }
    }

    suspend fun getEntries(): Flow<List<EntriesAdapterItem>> {
        val start = System.currentTimeMillis()
        var reported = false

        return combine(
            feedsRepository.getAll(),
            entriesRepository.count(),
            getShowReadEntries(),
            getShowPreviewImages(),
            getCropPreviewImages(),
        ) { feeds, _, showViewedEntries, showPreviewImages, cropPreviewImages ->
            val entries = if (showViewedEntries) {
                entriesRepository.getAll().first()
            } else {
                entriesRepository.getByViewed(false).first()
            }

            Timber.d("Got ${entries.size} results in ${System.currentTimeMillis() - start} ms")

            val result = entries.filter {
                showViewedEntries || !it.viewed
            }.map {
                val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
                it.toRow(feed, showPreviewImages, cropPreviewImages)
            }

            if (!reported) {
                reported = true
                Timber.d("Prepared results in ${System.currentTimeMillis() - start} ms")
            }

            result
        }
    }

    suspend fun performInitialSyncIfNecessary() {
        if (!prefs.initialSyncCompleted().first()) {
            newsApiSync.performInitialSync()
            syncPreviews()
        }
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
        syncPreviews()
    }

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

    suspend fun isInitialSyncCompleted() = prefs.initialSyncCompleted().first()

    suspend fun downloadPodcast(id: String) {
        entriesAudioRepository.downloadPodcast(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    suspend fun markAsViewed(entryId: String) {
        entriesRepository.setViewed(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    suspend fun markAsViewedAndBookmarked(entryId: String) = withContext(Dispatchers.IO) {
        entriesRepository.setViewed(entryId, true)
        entriesRepository.setBookmarked(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    private suspend fun getShowReadEntries() = prefs.showReadEntries()

    private suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    private suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        showFeedImages: Boolean,
        cropFeedImages: Boolean,
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
            viewed = viewed,
            podcast = isPodcast(),
            podcastDownloadPercent = flow {
                entriesAudioRepository.getDownloadProgress(this@toRow.id).collect {
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
            showImage = showFeedImages,
            cropImage = cropFeedImages,
            supportingText = flow { emit(entriesSupportingTextRepository.getSupportingText(this@toRow.id)) },
            cachedSupportingText = entriesSupportingTextRepository.getSupportingText(this.id),
        )
    }
}