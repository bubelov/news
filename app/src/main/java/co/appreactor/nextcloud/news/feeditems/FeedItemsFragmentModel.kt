package co.appreactor.nextcloud.news.feeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.common.*
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import co.appreactor.nextcloud.news.podcasts.PodcastDownloadsRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.*

class FeedItemsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val feedItemsSummariesRepository: FeedItemsSummariesRepository,
    private val openGraphImagesRepository: OpenGraphImagesRepository,
    private val podcastsRepository: PodcastDownloadsRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    init {
        viewModelScope.launch {
            podcastsRepository.deleteCompletedDownloadsWithoutFiles()
            podcastsRepository.deletePartialDownloads()
        }

        viewModelScope.launch {
            openGraphImagesRepository.warmUpMemoryCache()
        }
    }

    suspend fun getFeedItems() = combine(
        feedsRepository.all(),
        feedItemsRepository.all(),
        getShowReadNews(),
        getShowFeedImages(),
        getCropFeedImages()
    ) { feeds, feedItems, showReadNews, showFeedImages, cropFeedImages ->
        feedItems.filter {
            showReadNews || it.unread
        }.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId.toString() }
            it.toRow(feed, showFeedImages, cropFeedImages)
        }
    }

    suspend fun performInitialSyncIfNoData() {
        newsApiSync.performInitialSyncIfNotDone()
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.initialSyncCompleted().first()

    suspend fun downloadPodcast(id: Long) {
        podcastsRepository.downloadPodcast(id)
    }

    suspend fun getFeedItem(id: Long) = feedItemsRepository.byId(id).first()

    suspend fun markAsRead(feedItemId: Long) {
        feedItemsRepository.updateUnread(feedItemId, false)
        newsApiSync.syncFeedItemsFlags()
    }

    suspend fun markAsReadAndStarred(feedItemId: Long) = withContext(Dispatchers.IO) {
        feedItemsRepository.updateUnread(feedItemId, false)
        feedItemsRepository.updateStarred(feedItemId, true)
        newsApiSync.syncFeedItemsFlags()
    }

    private suspend fun getShowReadNews() = prefs.showReadNews()

    private suspend fun getShowFeedImages() = prefs.showFeedImages()

    private suspend fun getCropFeedImages() = prefs.cropFeedImages()

    private suspend fun FeedItem.toRow(
        feed: Feed?,
        showFeedImages: Boolean,
        cropFeedImages: Boolean,
    ): FeedItemsAdapterItem {
        val dateString = DateFormat.getDateInstance().format(Date(pubDate * 1000))

        return FeedItemsAdapterItem(
            id = id,
            title = title,
            (feed?.title ?: "Unknown feed") + " · " + dateString,
            unread = unread,
            podcast = isPodcast(),
            podcastDownloadPercent = flow {
                podcastsRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flow {
                openGraphImagesRepository.parse(this@toRow)

                openGraphImagesRepository.getImage(this@toRow).collect {
                    emit(it)
                }
            },
            cachedImage = openGraphImagesRepository.getImage(this).first(),
            showImage = showFeedImages,
            cropImage = cropFeedImages,
            summary = flow { emit(feedItemsSummariesRepository.getSummary(this@toRow)) },
            cachedSummary = feedItemsSummariesRepository.getCachedSummary(this.id)
        )
    }
}