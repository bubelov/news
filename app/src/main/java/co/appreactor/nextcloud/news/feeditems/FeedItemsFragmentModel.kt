package co.appreactor.nextcloud.news.feeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import co.appreactor.nextcloud.news.podcasts.PodcastDownloadsRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class FeedItemsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
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
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, showFeedImages, cropFeedImages)
        }
    }

    suspend fun getShowReadNews() = prefs.getBoolean(
        key = Preferences.SHOW_READ_NEWS,
        default = true
    )

    suspend fun setShowReadNews(show: Boolean) {
        prefs.putBoolean(
            key = Preferences.SHOW_READ_NEWS,
            value = show
        )
    }

    suspend fun performInitialSyncIfNoData() {
        newsApiSync.performInitialSyncIfNoData()
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.getBoolean(
        key = Preferences.INITIAL_SYNC_COMPLETED,
        default = false
    ).first()

    suspend fun downloadPodcast(id: Long) {
        podcastsRepository.downloadPodcast(id)
    }

    suspend fun getFeedItem(id: Long) = feedItemsRepository.byId(id).first()

    private suspend fun getShowFeedImages() = prefs.getBoolean(
        key = Preferences.SHOW_FEED_IMAGES,
        default = true
    )

    private suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )

    private fun FeedItem.toRow(feed: Feed?, showFeedImages: Boolean, cropFeedImages: Boolean): FeedItemsAdapterRow {
        val dateString = DateFormat.getDateInstance().format(Date(pubDate * 1000))

        return FeedItemsAdapterRow(
            id,
            title,
            (feed?.title ?: "Unknown feed") + " · " + dateString,
            unread,
            isPodcast(),
            podcastDownloadPercent = flow {
                podcastsRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            imageUrl = flow {
                openGraphImagesRepository.getImageUrl(this@toRow).collect {
                    emit(it)
                }
            },
            showImage = showFeedImages,
            cropImage = cropFeedImages,
            summary = flow { emit(getSummary()) },
        )
    }
}