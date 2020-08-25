package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository
) : ViewModel() {

    suspend fun getNewsAndFeeds(): Flow<Pair<List<NewsItem>, List<NewsFeed>>> {
        return newsItemsRepository.all().map {
            val feeds = newsFeedsRepository.all()
            Pair(it, feeds)
        }
    }
}