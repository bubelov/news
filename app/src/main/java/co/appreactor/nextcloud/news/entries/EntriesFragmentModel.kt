package co.appreactor.nextcloud.news.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.common.*
import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.opengraph.EntriesImagesRepository
import co.appreactor.nextcloud.news.podcasts.EntriesAudioRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.*

class EntriesFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSummariesRepository: EntriesSummariesRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val entriesAudioRepository: EntriesAudioRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    init {
        viewModelScope.launch {
            entriesAudioRepository.deleteCompletedDownloadsWithoutFiles()
            entriesAudioRepository.deletePartialDownloads()
        }

        viewModelScope.launch {
            entriesImagesRepository.warmUpMemoryCache()
        }
    }

    suspend fun getEntries() = combine(
        feedsRepository.getAll(),
        entriesRepository.getAll(),
        getShowReadEntries(),
        getShowPreviewImages(),
        getCropPreviewImages()
    ) { feeds, entries, showReadEntries, showPreviewImages, cropPreviewImages ->
        entries.filter {
            showReadEntries || it.unread
        }.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId.toString() }
            it.toRow(feed, showPreviewImages, cropPreviewImages)
        }
    }

    suspend fun performInitialSyncIfNoData() {
        newsApiSync.performInitialSyncIfNotDone()
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.initialSyncCompleted().first()

    suspend fun downloadPodcast(id: String) {
        entriesAudioRepository.downloadPodcast(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    suspend fun markAsRead(entryId: String) {
        entriesRepository.setUnread(entryId, false)
        newsApiSync.syncEntriesFlags()
    }

    suspend fun markAsReadAndStarred(entryId: String) = withContext(Dispatchers.IO) {
        entriesRepository.setUnread(entryId, false)
        entriesRepository.setStarred(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    private suspend fun getShowReadEntries() = prefs.showReadEntries()

    private suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    private suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    private suspend fun Entry.toRow(
        feed: Feed?,
        showFeedImages: Boolean,
        cropFeedImages: Boolean,
    ): EntriesAdapterItem {
        val dateString = DateFormat.getDateInstance().format(Date(pubDate * 1000))

        return EntriesAdapterItem(
            id = id,
            title = title,
            (feed?.title ?: "Unknown feed") + " · " + dateString,
            unread = unread,
            podcast = isPodcast(),
            podcastDownloadPercent = flow {
                entriesAudioRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flow {
                entriesImagesRepository.parse(this@toRow)

                entriesImagesRepository.getImage(this@toRow).collect {
                    emit(it)
                }
            },
            cachedImage = entriesImagesRepository.getImage(this).first(),
            showImage = showFeedImages,
            cropImage = cropFeedImages,
            summary = flow { emit(entriesSummariesRepository.getSummary(this@toRow)) },
            cachedSummary = entriesSummariesRepository.getCachedSummary(this.id)
        )
    }
}