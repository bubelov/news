package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsItem

class NewsItemFragmentModel(
    private val newsItemsRepository: NewsItemsRepository
) : ViewModel() {

    suspend fun getNewsItem(id: Long): NewsItem {
        return newsItemsRepository.all().single { it.id == id }
    }
}