package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val sync: Sync
) : ViewModel() {

    val showReadNews = MutableStateFlow(true)

    suspend fun getNewsAndFeeds(): Flow<Pair<List<NewsItem>, List<NewsFeed>>> = flow {
        newsItemsRepository.all().collect { allNews ->
            showReadNews.collect { showReadNews ->
                val news = if (showReadNews) {
                    allNews
                } else {
                    allNews.filter { it.unread }
                }

                val feeds = newsFeedsRepository.all()

                emit(Pair(news, feeds))
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            sync.sync()
        }
    }
}