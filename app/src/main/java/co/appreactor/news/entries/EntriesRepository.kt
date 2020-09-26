package co.appreactor.news.entries

import co.appreactor.news.api.*
import co.appreactor.news.common.Preferences
import co.appreactor.news.db.Entry
import co.appreactor.news.db.EntryQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import retrofit2.Response

@Suppress("BlockingMethodInNonBlockingContext")
class EntriesRepository(
    private val db: EntryQueries,
    private val api: NewsApi,
    private val prefs: Preferences,
) {

    suspend fun getAll() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun get(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun getViewed() = withContext(Dispatchers.IO) {
        db.selectByViewed(viewed = true).asFlow().mapToList()
    }

    suspend fun getNotViewed() = withContext(Dispatchers.IO) {
        db.selectByViewed(viewed = false).asFlow().mapToList()
    }

    suspend fun setViewed(id: String, viewed: Boolean) = withContext(Dispatchers.IO) {
        db.updateViewed(
            viewed = viewed,
            id = id
        )
    }

    suspend fun getBookmarked() = withContext(Dispatchers.IO) {
        db.selectBookmarked().asFlow().mapToList()
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        db.updateBookmarked(
            bookmarked = bookmarked,
            id = id
        )
    }

    suspend fun getNotViewedAndBookmarked() = withContext(Dispatchers.IO) {
        db.selectNotViewedAndBookmarked().asFlow().mapToList()
    }

    suspend fun getCount() = withContext(Dispatchers.IO) {
        db.selectCount().asFlow().mapToOne()
    }

    private suspend fun getMaxUpdated() = withContext(Dispatchers.IO) {
        db.selectMaxUpdaded().executeAsOneOrNull()?.MAX
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    fun deleteByFeedId(feedId: String) {
        db.deleteByFeedId(feedId)
    }

    suspend fun syncNotViewedAndBookmarked() = withContext(Dispatchers.IO) {
        val count = db.selectCount().executeAsOne()

        if (count > 0) {
            return@withContext
        }

        val notViewed = api.getUnreadItems().execute().body()!!
        val bookmarked = api.getStarredItems().execute().body()!!

        db.transaction {
            (notViewed.items + bookmarked.items).mapNotNull { it.toEntry() }.forEach {
                db.insertOrReplace(it)
            }
        }

        prefs.putString(
            key = Preferences.LAST_ENTRIES_SYNC_DATE_TIME,
            value = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString()
        )
    }

    suspend fun syncViewedFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = db.selectByViewedSynced(false).executeAsList()

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedViewedEntries = unsyncedItems.filter { it.viewed }

        if (unsyncedViewedEntries.isNotEmpty()) {
            val response = api.putRead(PutReadArgs(
                unsyncedViewedEntries.map { it.id.toLong() }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedViewedEntries.forEach {
                        db.updateViewedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedNotViewedEntries = unsyncedItems.filterNot { it.viewed }

        if (unsyncedNotViewedEntries.isNotEmpty()) {
            val response = api.putUnread(PutReadArgs(
                unsyncedNotViewedEntries.map { it.id.toLong() }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedNotViewedEntries.forEach {
                        db.updateViewedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncBookmarkedFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = db.selectByBookmarkedSynced(false).executeAsList()

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedBookmarkedEntries = unsyncedItems.filter { it.bookmarked }

        if (unsyncedBookmarkedEntries.isNotEmpty()) {
            val response = api.putStarred(PutStarredArgs(unsyncedBookmarkedEntries.map {
                PutStarredArgsItem(
                    it.feedId.toLong(),
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedBookmarkedEntries.forEach {
                        db.updateBookmarkedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedNotBookmarkedEntries = unsyncedItems.filterNot { it.bookmarked }

        if (unsyncedNotBookmarkedEntries.isNotEmpty()) {
            val response = api.putUnstarred(PutStarredArgs(unsyncedNotBookmarkedEntries.map {
                PutStarredArgsItem(
                    it.feedId.toLong(),
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedNotBookmarkedEntries.forEach {
                        db.updateBookmarkedSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncNewAndUpdated() = withContext(Dispatchers.IO) {
        val threshold = getMaxUpdated() ?: prefs.getString(Preferences.LAST_ENTRIES_SYNC_DATE_TIME).first()

        if (threshold.isBlank()) {
            throw Exception("Can not find any reference dates")
        }

        val thresholdSeconds = LocalDateTime.parse(threshold).toInstant(TimeZone.UTC).epochSeconds
        val response = api.getNewAndUpdatedItems(thresholdSeconds + 1).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val items = response.body()?.items ?: throw Exception("Can not parse server response")

        db.transaction {
            items.mapNotNull { it.toEntry() }.forEach {
                db.insertOrReplace(it)
            }
        }

        prefs.putString(
            key = Preferences.LAST_ENTRIES_SYNC_DATE_TIME,
            value = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString()
        )
    }

    private fun ItemJson.toEntry(): Entry? {
        if (id == null) return null
        if (pubDate == null) return null
        if (lastModified == null) return null
        if (unread == null) return null
        if (starred == null) return null

        val published = Instant.fromEpochSeconds(pubDate).toLocalDateTime(TimeZone.UTC)
        val updated = Instant.fromEpochSeconds(lastModified).toLocalDateTime(TimeZone.UTC)

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

    private fun Response<*>.toException() = Exception("HTTPS request failed with error code ${code()}")
}