package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.db.NewsItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NewsItemFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNewsItem(id: Long): NewsItem {
        return newsItemsRepository.byId(id).first()!!
    }

    suspend fun getReadFlag(id: Long) = newsItemsRepository.byId(id).map { it?.unread == false }

    suspend fun getStarredFlag(id: Long) = newsItemsRepository.byId(id).map { it?.starred == true }

    suspend fun toggleReadFlag(id: Long) {
        val item = getNewsItem(id)
        newsItemsRepository.updateUnread(id, !item.unread)
        sync.syncNewsFlagsOnly()
    }

    suspend fun toggleStarredFlag(id: Long) {
        val item = getNewsItem(id)
        newsItemsRepository.updateStarred(id, !item.starred)
        sync.syncNewsFlagsOnly()
    }

    suspend fun markAsRead(id: Long) {
        newsItemsRepository.updateUnread(id, false)
        sync.syncNewsFlagsOnly()
    }
}