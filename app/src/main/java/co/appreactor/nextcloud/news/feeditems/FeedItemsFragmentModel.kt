package co.appreactor.nextcloud.news.feeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import co.appreactor.nextcloud.news.podcasts.PodcastsRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class FeedItemsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val openGraphImagesRepository: OpenGraphImagesRepository,
    private val podcastsRepository: PodcastsRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    init {
        viewModelScope.launch {
            podcastsRepository.verifyCache()
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
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
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

    private suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )

    private fun FeedItem.toRow(feed: Feed?, cropFeedImages: Boolean): FeedItemsAdapterRow {
        val dateString = DateFormat.getDateInstance().format(Date(pubDate * 1000))

        return FeedItemsAdapterRow(
            id,
            title,
            (feed?.title ?: "Unknown feed") + " · " + dateString,
            unread,
            cropFeedImages,
            isPodcast(),
            enclosureDownloadProgress,
            summary = flow { emit(getSummary()) },
            imageUrl = flow { emit(openGraphImagesRepository.getImageUrl(this@toRow)) },
        )
    }
}