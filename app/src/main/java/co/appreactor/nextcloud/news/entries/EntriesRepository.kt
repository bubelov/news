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
import kotlinx.datetime.*

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

    suspend fun get(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun setViewed(id: String, viewed: Boolean) = withContext(Dispatchers.IO) {
        db.updateViewed(
            viewed = viewed,
            id = id
        )
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        db.updateBookmarked(
            bookmarked = bookmarked,
            id = id
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    fun deleteByFeedId(feedId: String) {
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
            !it.viewedSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedViewedItems = unsyncedItems.filter { it.viewed }

        if (unsyncedViewedItems.isNotEmpty()) {
            val response = api.putRead(PutReadArgs(
                unsyncedViewedItems.map { it.id.toLong() }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedViewedItems.forEach {
                        db.updateViewedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedNotViewedItems = unsyncedItems.filterNot { it.viewed }

        if (unsyncedNotViewedItems.isNotEmpty()) {
            val response = api.putUnread(PutReadArgs(
                unsyncedNotViewedItems.map { it.id.toLong() }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedNotViewedItems.forEach {
                        db.updateViewedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncStarredFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = getAll().first().filter {
            !it.bookmarkedSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedBookmarkedItems = unsyncedItems.filter { it.bookmarked }

        if (unsyncedBookmarkedItems.isNotEmpty()) {
            val response = api.putStarred(PutStarredArgs(unsyncedBookmarkedItems.map {
                PutStarredArgsItem(
                    it.feedId.toLong(),
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedBookmarkedItems.forEach {
                        db.updateBookmarkedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedNotBookmarkedItems = unsyncedItems.filterNot { it.bookmarked }

        if (unsyncedNotBookmarkedItems.isNotEmpty()) {
            val response = api.putUnstarred(PutStarredArgs(unsyncedNotBookmarkedItems.map {
                PutStarredArgsItem(
                    it.feedId.toLong(),
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedNotBookmarkedItems.forEach {
                        db.updateBookmarkedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncNewAndUpdated() = withContext(Dispatchers.IO) {
        val mostRecentItem = getAll().firstOrNull()?.maxByOrNull { it.updated } ?: return@withContext
        val epochSeconds = LocalDateTime.parse(mostRecentItem.updated).toInstant(TimeZone.UTC).epochSeconds

        val newAndUpdatedItems = api.getNewAndUpdatedItems(epochSeconds + 1).execute().body()!!.items

        db.transaction {
            newAndUpdatedItems.mapNotNull { it.toEntry() }.forEach {
                db.insertOrReplace(it)
            }
        }
    }

    private fun ItemJson.toEntry(): Entry? {
        if (id == null) return null
        if (pubDate == null) return null
        if (lastModified == null) return null
        if (unread == null) return null
        if (starred == null) return null

        val published = Instant.fromEpochSeconds(pubDate).toLocalDateTime(TimeZone.currentSystemDefault())
        val updated = Instant.fromEpochSeconds(lastModified).toLocalDateTime(TimeZone.currentSystemDefault())

        return Entry(
            id = id.toString(),
            feedId = feedId?.toString() ?: "",
            title = title ?: "Untitled",
            link = url?.replace("http://", "https://") ?: "",
            published = published.toString(),
            updated = updated.toString(),
            authorName = author ?: "",
            summary = body ?: "No content",
            enclosureLink = enclosureLink?.replace("http://", "https://") ?: "",
            enclosureLinkType = enclosureMime ?: "",

            viewed = unread == false,
            viewedSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = guidHash ?: return null,
        )
    }
}