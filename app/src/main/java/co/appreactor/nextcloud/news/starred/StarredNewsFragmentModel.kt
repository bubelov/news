package co.appreactor.nextcloud.news.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.news.NewsAdapterRow
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import co.appreactor.nextcloud.news.podcasts.PodcastsSync
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class StarredNewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences,
    private val podcastsSync: PodcastsSync
) : ViewModel() {

    suspend fun getNewsItems() = newsItemsRepository.all().combine(getCropFeedImages()) { unfilteredItems, cropImages ->
        val items = unfilteredItems.filter { it.starred }
        val feeds = newsFeedsRepository.all()

        items.map {
            val dateString = DateFormat.getDateInstance().format(Date(it.pubDate * 1000))
            val feed = feeds.single { feed -> feed.id == it.feedId }

            NewsAdapterRow(
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

    suspend fun isInitialSyncCompleted() =
        prefs.getBoolean(Preferences.INITIAL_SYNC_COMPLETED, false)

    fun downloadPodcast(id: Long) {
        viewModelScope.launch {
            podcastsSync.downloadPodcast(id)
        }
    }

    suspend fun getNewsItem(id: Long) = newsItemsRepository.byId(id)

    private suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )
}