package feeds

import api.Api
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.Entry
import db.Feed
import db.Link
import db.SelectAllWithUnreadEntryCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.koin.core.annotation.Single

//CREATE TABLE Feed (
//id TEXT PRIMARY KEY NOT NULL,
//links TEXT AS List<Link> NOT NULL,
//title TEXT NOT NULL,
//ext_open_entries_in_browser INTEGER AS Boolean,
//ext_blocked_words TEXT NOT NULL,
//ext_show_preview_images INTEGER AS Boolean
//);

@Single
class FeedsRepo(
    private val api: Api,
    private val db: Db,
) {

    suspend fun insertOrReplace(feed: Feed) {
//        insertOrReplace:
//        INSERT OR REPLACE
//        INTO Feed(id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images)
//        VALUES ?;
        withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feed)
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Pair<Feed, List<Entry>> {
        val feedWithEntries = api.addFeed(url).getOrThrow()

        return withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feedWithEntries.first)
            feedWithEntries
        }
    }

    fun selectAll(): Flow<List<Feed>> {
//        selectAll:
//        SELECT *
//        FROM Feed
//        ORDER BY title;
        return db.feedQueries.selectAll().asFlow().mapToList()
    }

    fun selectAllWithUnreadEntryCount(): Flow<List<SelectAllWithUnreadEntryCount>> {
//        selectAllWithUnreadEntryCount:
//        SELECT f.id, f.links, f.title, count(e.id) AS unread_entries
//        FROM Feed f
//        LEFT JOIN Entry e ON e.feed_id = f.id AND e.ext_read = 0 AND e.ext_bookmarked = 0
//        GROUP BY f.id
//        ORDER BY f.title;
        return db.feedQueries.selectAllWithUnreadEntryCount().asFlow().mapToList()
    }

    fun selectById(id: String): Flow<Feed?> {
//        selectById:
//        SELECT *
//        FROM Feed
//        WHERE id = ?;
        return db.feedQueries.selectById(id).asFlow().mapToOneOrNull()
    }

    fun selectLinks(): Flow<List<Link>> {
//        selectLinks:
//        SELECT links
//        FROM Entry;
        return db.feedQueries.selectLinks().asFlow().mapToList().map { it.flatten() }
    }

    suspend fun updateTitle(feedId: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            val feed = db.feedQueries.selectById(feedId).executeAsOneOrNull()
                ?: throw Exception("Cannot find feed $feedId in cache")
            val trimmedNewTitle = newTitle.trim()
            api.updateFeedTitle(feedId, trimmedNewTitle)
            db.feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }
    }

    suspend fun deleteById(id: String) {
        api.deleteFeed(id)

        withContext(Dispatchers.IO) {
            db.transaction {
                db.feedQueries.deleteById(id)
//                deleteByFeedId:
//                DELETE
//                FROM Entry
//                WHERE feed_id = ?;
                db.entryQueries.deleteByFeedId(id)
            }
        }
    }

    suspend fun sync() {
        withContext(Dispatchers.IO) {
            val newFeeds = api.getFeeds().getOrThrow().sortedBy { it.id }
            val cachedFeeds = selectAll().first().sortedBy { it.id }

            db.transaction {
                db.feedQueries.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.id }

                    db.feedQueries.insertOrReplace(
                        feed.copy(
                            ext_open_entries_in_browser = cachedFeed?.ext_open_entries_in_browser ?: false,
                            ext_blocked_words = cachedFeed?.ext_blocked_words ?: "",
                            ext_show_preview_images = cachedFeed?.ext_show_preview_images,
                        )
                    )
                }
            }
        }
    }
}