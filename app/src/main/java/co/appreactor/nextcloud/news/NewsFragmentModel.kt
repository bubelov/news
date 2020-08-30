package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences,
    private val sync: Sync
) : ViewModel() {

    val showReadNews = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            prefs.getBoolean(Preferences.SHOW_READ_NEWS, true).collect {
                showReadNews.value = it
            }
        }
    }

    suspend fun getNewsItems(): Flow<List<NewsItem>> =
        newsItemsRepository.all().combine(showReadNews) { items, showRead ->
            if (showRead) {
                return@combine items
            } else {
                return@combine items.filter { it.unread }
            }
        }

    suspend fun getFeeds() = newsFeedsRepository.all()

    suspend fun performInitialSyncIfNoData() {
        sync.performInitialSyncIfNoData()
    }

    suspend fun sync() {
        sync.sync()
    }
}