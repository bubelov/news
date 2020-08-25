package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsItemQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NewsItemsRepository(
    private val cache: NewsItemQueries,
    private val api: NewsApi
) {

    suspend fun all() = withContext(Dispatchers.IO) {
        if (cache.findAll().executeAsList().isEmpty()) {
            val unread = api.getUnreadItems()
            val starred = api.getStarredItems()

            cache.transaction {
                (unread.items + starred.items).forEach {
                    cache.insertOrReplace(it.copy(unreadSynced = true))
                }
            }
        }

        cache.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun updateUnread(id: Long, unread: Boolean) = withContext(Dispatchers.IO) {
        cache.updateUnread(
            unread = unread,
            id = id
        )
    }
}