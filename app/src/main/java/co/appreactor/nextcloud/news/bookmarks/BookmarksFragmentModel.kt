package co.appreactor.nextcloud.news.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.cropFeedImages
import co.appreactor.nextcloud.news.common.showFeedImages
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.feeditems.*
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import co.appreactor.nextcloud.news.podcasts.PodcastDownloadsRepository
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class BookmarksFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val feedItemsSummariesRepository: FeedItemsSummariesRepository,
    private val openGraphImagesRepository: OpenGraphImagesRepository,
    private val podcastsRepository: PodcastDownloadsRepository,
    private val prefs: Preferences,
) : ViewModel() {

    init {
        viewModelScope.launch {
            openGraphImagesRepository.warmUpMemoryCache()
        }
    }

    suspend fun getBookmarks() = combine(
        feedsRepository.all(),
        feedItemsRepository.starred(),
        getShowFeedImages(),
        getCropFeedImages(),
    ) { feeds, feedItems, showFeedImages, cropFeedImages ->
        feedItems.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, showFeedImages, cropFeedImages)
        }
    }

    suspend fun downloadPodcast(id: Long) {
        podcastsRepository.downloadPodcast(id)
    }

    suspend fun getFeedItem(id: Long) = feedItemsRepository.byId(id).first()

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
            unread = true,
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