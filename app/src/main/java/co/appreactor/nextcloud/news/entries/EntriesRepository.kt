package co.appreactor.nextcloud.news.entries

import co.appreactor.nextcloud.news.api.*
import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.db.EntryQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@Suppress("BlockingMethodInNonBlockingContext")
class EntriesRepository(
    private val db: EntryQueries,
    private val api: NewsApi,
) {

    suspend fun getAll() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun getUnread() = withContext(Dispatchers.IO) {
        db.selectUnread().asFlow().mapToList()
    }

    suspend fun getStarred() = withContext(Dispatchers.IO) {
        db.selectStarred().asFlow().mapToList()
    }

    suspend fun get(id: Long) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun setUnread(id: Long, unread: Boolean) = withContext(Dispatchers.IO) {
        db.updateUnread(
            unread = unread,
            id = id
        )
    }

    suspend fun setStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
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

    suspend fun syncUnreadAndStarred() = withContext(Dispatchers.IO) {
        val count = db.selectCount().executeAsOne()

        if (count > 0) {
            return@withContext
        }

        val unread = api.getUnreadItems().execute().body()!!
        val starred = api.getStarredItems().execute().body()!!

        db.transaction {
            (unread.items + starred.items).mapNotNull { it.toEntry() }.forEach {
                db.insertOrReplace(it)
            }
        }
    }

    suspend fun syncUnreadFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = getAll().first().filter {
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
        val unsyncedItems = getAll().first().filter {
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

    suspend fun syncNewAndUpdated() = withContext(Dispatchers.IO) {
        val mostRecentItem = getAll().firstOrNull()?.maxByOrNull { it.lastModified } ?: return@withContext

        val newAndUpdatedItems = api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1).execute().body()!!.items

        db.transaction {
            newAndUpdatedItems.mapNotNull { it.toEntry() }.forEach {
                db.insertOrReplace(it)
            }
        }
    }

    private fun ItemJson.toEntry(): Entry? {
        return Entry(
            id = id ?: return null,
            guidHash = guidHash ?: return null,
            url = url?.replace("http://", "https://") ?: "",
            title = title ?: "Untitled",
            author = author ?: "",
            pubDate = pubDate ?: 0,
            body = body ?: "No content",
            enclosureMime = enclosureMime ?: "",
            enclosureLink = enclosureLink?.replace("http://", "https://") ?: "",
            feedId = feedId ?: 0,
            unread = unread ?: return null,
            unreadSynced = true,
            starred = starred ?: return null,
            starredSynced = true,
            lastModified = lastModified ?: 0
        )
    }
}