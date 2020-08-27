package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StarredNewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNewsAndFeeds(): Flow<Pair<List<NewsItem>, List<NewsFeed>>> = flow {
        newsItemsRepository.all().collect { allNews ->
                val feeds = newsFeedsRepository.all()
                emit(Pair(allNews.filter { it.starred }, feeds))
        }
    }

    fun sync() {
        viewModelScope.launch {
            sync.sync()
        }
    }
}