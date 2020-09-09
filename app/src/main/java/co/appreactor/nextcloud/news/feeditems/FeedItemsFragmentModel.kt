package co.appreactor.nextcloud.news.feeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.DatabaseSyncManager
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesManager
import co.appreactor.nextcloud.news.podcasts.PodcastsManager
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class FeedItemsFragmentModel(
    private val feedItemsRepository: FeedItemsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences,
    private val sync: DatabaseSyncManager,
    private val imagesSync: OpenGraphImagesManager,
    private val podcastsSync: PodcastsManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            imagesSync.start()
        }

        viewModelScope.launch {
            podcastsSync.verifyCache()
        }
    }

    suspend fun getFeedItems() = combine(
        feedsRepository.all(),
        feedItemsRepository.all(),
        getShowReadNews(),
        getCropFeedImages()
    ) { feeds, feedItems, showReadNews, cropFeedImages ->
        feedItems.filter {
            showReadNews || it.unread
        }.map {
            val feed = feeds.single { feed -> feed.id == it.feedId }
            it.toRow(feed, cropFeedImages)
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
        sync.performInitialSyncIfNoData()
    }

    suspend fun performFullSync() {
        sync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.getBoolean(
        key = Preferences.INITIAL_SYNC_COMPLETED,
        default = false
    ).first()

    suspend fun downloadPodcast(id: Long) {
        podcastsSync.downloadPodcast(id)
    }

    suspend fun getFeedItem(id: Long) = feedItemsRepository.byId(id).first()

    private suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )

    private fun FeedItem.toRow(feed: Feed, cropFeedImages: Boolean): FeedItemsAdapterRow {
        val dateString = DateFormat.getDateInstance().format(Date(pubDate * 1000))

        return FeedItemsAdapterRow(
            id,
            title,
            feed.title + " · " + dateString,
            summary,
            unread,
            openGraphImageUrl,
            cropFeedImages,
            isPodcast(),
            enclosureDownloadProgress
        )
    }
}