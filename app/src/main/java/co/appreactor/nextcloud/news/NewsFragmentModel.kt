package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.*

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val sync: Sync
) : ViewModel() {

    val showReadNews = MutableStateFlow(true)

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