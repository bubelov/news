package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsItemQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

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

    suspend fun syncUnreadFlags() = withContext(Dispatchers.IO) {
        Timber.d("Syncing unread flags")

        val unsyncedItems = all().first().filter {
            !it.unreadSynced
        }

        Timber.d("Unsynced items: ${unsyncedItems.size}")

        val unsyncedReadItems = unsyncedItems.filterNot { it.unread }
        val unsyncedUnreadItems = unsyncedItems.filter { it.unread }
        Timber.d("Of them, read: ${unsyncedReadItems.size}")
        Timber.d("Of them, unread: ${unsyncedUnreadItems.size}")

        api.markAsRead(NewsItemsIdsArgs(
            unsyncedReadItems.map { it.id }
        ))

        cache.transaction {
            unsyncedReadItems.forEach {
                cache.updateUnreadSynced(true, it.id)
            }
        }

        api.markAsUnread(
            NewsItemsIdsArgs(
                unsyncedUnreadItems.map { it.id }
            )
        )

        cache.transaction {
            unsyncedUnreadItems.forEach {
                cache.updateUnreadSynced(true, it.id)
            }
        }

        Timber.d("Finished syncing unread flags")
    }

    suspend fun fetchNewAndUpdatedItems() = withContext(Dispatchers.IO) {
        Timber.d("Syncing new and updated items")
        val mostRecentItem = all().first().maxByOrNull { it.lastModified }

        if (mostRecentItem == null) {
            Timber.d("Cache is empty, cancelling")
            return@withContext
        }

        val items = api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1)
        Timber.d("New and updated items: ${items.items.size}")

        cache.transaction {
            items.items.forEach {
                cache.insertOrReplace(it.copy(unreadSynced = true))
            }
        }

        Timber.d("Finished syncing new and updated items")
    }
}