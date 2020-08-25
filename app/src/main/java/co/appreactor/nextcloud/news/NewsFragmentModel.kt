package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem

class NewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository
) : ViewModel() {

    suspend fun getNewsAndFeeds(): Pair<List<NewsItem>, List<NewsFeed>> {
        return Pair(newsItemsRepository.all(), newsFeedsRepository.all())
    }
}