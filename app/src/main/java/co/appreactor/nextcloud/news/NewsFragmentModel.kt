package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNewsAndFeeds(): Flow<Pair<List<NewsItem>, List<NewsFeed>>> {
        return newsItemsRepository.all().map {
            val feeds = newsFeedsRepository.all()
            Pair(it, feeds)
        }
    }

    fun sync() {
        viewModelScope.launch {
            sync.sync()
        }
    }
}