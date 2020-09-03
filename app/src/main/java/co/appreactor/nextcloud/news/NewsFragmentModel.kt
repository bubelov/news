package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNews() = newsItemsRepository.all().combine(getShowReadNews()) { items, showRead ->
        if (showRead) {
            return@combine items
        } else {
            return@combine items.filter { it.unread }
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
}