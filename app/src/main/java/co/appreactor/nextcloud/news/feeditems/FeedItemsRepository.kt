package co.appreactor.nextcloud.news.feeditems

import co.appreactor.nextcloud.news.api.*
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.db.FeedItemQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FeedItemsRepository(
    private val db: FeedItemQueries,
    private val api: NewsApi,
) {

    suspend fun create(feedItem: FeedItem) = withContext(Dispatchers.IO) {
        db.insertOrReplace(feedItem)
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        db.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun unread() = withContext(Dispatchers.IO) {
        db.findUnread().asFlow().map { it.executeAsList() }
    }

    suspend fun starred() = withContext(Dispatchers.IO) {
        db.findStarred().asFlow().map { it.executeAsList() }
    }

    suspend fun byId(id: Long) = withContext(Dispatchers.IO) {
        db.findById(id).asFlow().map { it.executeAsOneOrNull() }
    }

    suspend fun updateUnread(id: Long, unread: Boolean) = withContext(Dispatchers.IO) {
        db.updateUnread(
            unread = unread,
            id = id
        )
    }

    suspend fun updateStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        db.updateStarred(
            starred = starred,
            id = id
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    fun deleteByFeedId(feedId: Long) {
        db.deleteByFeedId(feedId)
    }

    suspend fun performInitialSyncIfNoData() = withContext(Dispatchers.IO) {
        val count = db.count().executeAsOne()

        if (count > 0) {
            return@withContext
        }

        val unread = api.getUnreadItems().execute().body()!!
        val starred = api.getStarredItems().execute().body()!!

        db.transaction {
            (unread.items + starred.items).mapNotNull { it.toFeedItem() }.forEach {
                db.insertOrReplace(it)
            }
        }
    }

    suspend fun syncUnreadFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = all().first().filter {
            !it.unreadSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedReadItems = unsyncedItems.filterNot { it.unread }

        if (unsyncedReadItems.isNotEmpty()) {
            val response = api.putRead(PutReadArgs(
                unsyncedReadItems.map { it.id }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedReadItems.forEach {
                        db.updateUnreadSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedUnreadItems = unsyncedItems.filter { it.unread }

        if (unsyncedUnreadItems.isNotEmpty()) {
            val response = api.putUnread(PutReadArgs(
                unsyncedUnreadItems.map { it.id }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedUnreadItems.forEach {
                        db.updateUnreadSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncStarredFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = all().first().filter {
            !it.starredSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedStarredItems = unsyncedItems.filter { it.starred }

        if (unsyncedStarredItems.isNotEmpty()) {
            val response = api.putStarred(PutStarredArgs(unsyncedStarredItems.map {
                PutStarredArgsItem(
                    it.feedId,
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedStarredItems.forEach {
                        db.updateStarredSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedUnstarredItems = unsyncedItems.filter { !it.starred }

        if (unsyncedUnstarredItems.isNotEmpty()) {
            val response = api.putUnstarred(PutStarredArgs(unsyncedUnstarredItems.map {
                PutStarredArgsItem(
                    it.feedId,
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedUnstarredItems.forEach {
                        db.updateStarredSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun fetchNewAndUpdatedItems() = withContext(Dispatchers.IO) {
        val mostRecentItem = all().firstOrNull()?.maxByOrNull { it.lastModified } ?: return@withContext

        val newAndUpdatedItems = api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1).execute().body()!!.items

        db.transaction {
            newAndUpdatedItems.mapNotNull { it.toFeedItem() }.forEach {
                db.insertOrReplace(it)
            }
        }
    }

    private fun FeedItemJson.toFeedItem(): FeedItem? {
        return FeedItem(
            id = id ?: return null,
            guidHash = guidHash ?: return null,
            url = url ?: "",
            title = title ?: "Untitled",
            author = author ?: "",
            pubDate = pubDate ?: 0,
            body = body ?: "No content",
            enclosureMime = enclosureMime ?: "",
            enclosureLink = enclosureLink ?: "",
            feedId = feedId ?: 0,
            unread = unread ?: return null,
            unreadSynced = true,
            starred = starred ?: return null,
            starredSynced = true,
            lastModified = lastModified ?: 0
        )
    }
}