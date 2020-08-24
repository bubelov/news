package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsFragmentModel(
    private val api: NewsApi
) : ViewModel() {

    suspend fun getNewsAndFeeds(): Pair<List<Item>, List<Feed>> {
        return withContext(Dispatchers.IO) {
            val feeds = api.getFeeds()
            val unread = api.getUnreadItems()
            val starred = api.getStarredItems()
            Pair(unread.items + starred.items, feeds.feeds)
        }
    }

    suspend fun markAsRead(item: Item) {
        withContext(Dispatchers.IO) {
            api.markAsRead(MarkAsReadArgs(listOf(item.id)))
        }
    }
}