package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.first

class NewsItemFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNewsItem(id: Long): NewsItem {
        return newsItemsRepository.all().first().single { it.id == id }
    }

    suspend fun markAsRead(id: Long) {
        newsItemsRepository.updateUnread(id, false)
        sync.syncNewsFlagsOnly()
    }
}