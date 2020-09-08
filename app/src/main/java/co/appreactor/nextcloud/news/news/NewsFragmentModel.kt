package co.appreactor.nextcloud.news.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.Sync
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesSync
import co.appreactor.nextcloud.news.podcasts.PodcastsSync
import co.appreactor.nextcloud.news.podcasts.isPodcast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences,
    private val sync: Sync,
    private val imagesSync: OpenGraphImagesSync,
    private val podcastsSync: PodcastsSync
) : ViewModel() {

    init {
        viewModelScope.launch {
            imagesSync.start()
        }

        viewModelScope.launch {
            podcastsSync.verifyCache()
        }
    }

    suspend fun getNews() = newsItemsRepository.all().combine(getShowReadNews()) { unfilteredItems, showRead ->
        val items = if (showRead) {
            unfilteredItems
        } else {
            unfilteredItems.filter { it.unread }
        }

        val feeds = newsFeedsRepository.all()

        return@combine items.map {
            val dateString = DateFormat.getDateInstance().format(Date(it.pubDate * 1000))
            val feed = feeds.single { feed -> feed.id == it.feedId }

            NewsAdapterRow(
                it.id,
                it.title,
                feed.title + " · " + dateString,
                it.summary,
                it.unread,
                it.openGraphImageUrl,
                it.isPodcast(),
                it.enclosureDownloadProgress
            )
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

    suspend fun getFeeds() = newsFeedsRepository.all()

    suspend fun performInitialSyncIfNoData() {
        sync.performInitialSyncIfNoData()
    }

    suspend fun performFullSync() {
        sync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.getBoolean(
        key = Preferences.INITIAL_SYNC_COMPLETED,
        default = false
    )

    fun downloadPodcast(id: Long) {
        viewModelScope.launch {
            podcastsSync.downloadPodcast(id)
        }
    }

    suspend fun getNewsItem(id: Long) = newsItemsRepository.byId(id)
}