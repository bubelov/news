package co.appreactor.nextcloud.news.bookmarks

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.feeditems.FeedItemsAdapterRow
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import co.appreactor.nextcloud.news.podcasts.PodcastsManager
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import java.text.DateFormat
import java.util.*

class BookmarksFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val podcastsManager: PodcastsManager,
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun getNewsItems() = feedItemsRepository.all().combine(getCropFeedImages()) { unfilteredItems, cropImages ->
        val items = unfilteredItems.filter { it.starred }
        val feeds = feedsRepository.all().first()

        items.map {
            val dateString = DateFormat.getDateInstance().format(Date(it.pubDate * 1000))
            val feed = feeds.single { feed -> feed.id == it.feedId }

            FeedItemsAdapterRow(
                it.id,
                it.title,
                feed.title + " · " + dateString,
                it.summary,
                true,
                it.openGraphImageUrl,
                cropImages,
                it.isPodcast(),
                it.enclosureDownloadProgress
            )
        }
    }

    suspend fun downloadPodcast(id: Long) {
        podcastsManager.downloadPodcast(id)
    }

    suspend fun getFeedItem(id: Long) = feedItemsRepository.byId(id).first()

    private suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )
}