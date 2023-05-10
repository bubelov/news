package feeds

import api.Api
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
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

@Single
class FeedsRepo(
    private val api: Api,
    private val db: Db,
) {

    suspend fun insertOrReplace(feed: Feed) {
        withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feed)
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Feed {
        val feed = api.addFeed(url).getOrThrow()

        return withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feed)
            feed
        }
    }

    fun selectAll(): Flow<List<Feed>> {
        return db.feedQueries.selectAll().asFlow().mapToList()
    }

    fun selectAllWithUnreadEntryCount(): Flow<List<SelectAllWithUnreadEntryCount>> {
        return db.feedQueries.selectAllWithUnreadEntryCount().asFlow().mapToList()
    }

    fun selectById(id: String): Flow<Feed?> {
        return db.feedQueries.selectById(id).asFlow().mapToOneOrNull()
    }

    fun selectLinks(): Flow<List<Link>> {
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