package feeds

import api.Api
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.koin.core.annotation.Single

@Single
class FeedsRepository(
    private val db: Db,
    private val api: Api,
) {

    suspend fun insertOrReplace(feed: Feed) {
        withContext(Dispatchers.Default) {
            db.feedQueries.insertOrReplace(feed)
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Feed {
        return withContext(Dispatchers.Default) {
            val feed = api.addFeed(url).getOrThrow()
            db.feedQueries.insertOrReplace(feed)
            feed
        }
    }

    fun selectAll(): Flow<List<Feed>> {
        return db.feedQueries.selectAll().asFlow().mapToList()
    }

    fun selectById(id: String): Flow<Feed?> {
        return db.feedQueries.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun updateTitle(feedId: String, newTitle: String) {
        withContext(Dispatchers.Default) {
            val feed = db.feedQueries.selectById(feedId).executeAsOneOrNull()
                ?: throw Exception("Cannot find feed $feedId in cache")
            val trimmedNewTitle = newTitle.trim()
            api.updateFeedTitle(feedId, trimmedNewTitle)
            db.feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }
    }

    suspend fun deleteById(id: String) {
        withContext(Dispatchers.Default) {
            api.deleteFeed(id)

            db.transaction {
                db.feedQueries.deleteById(id)
                db.entryQueries.deleteByFeedId(id)
            }
        }
    }

    suspend fun sync() {
        withContext(Dispatchers.Default) {
            val newFeeds = api.getFeeds().getOrThrow().sortedBy { it.id }
            val cachedFeeds = selectAll().first().sortedBy { it.id }

            db.transaction {
                db.feedQueries.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.id }

                    db.feedQueries.insertOrReplace(
                        feed.copy(
                            openEntriesInBrowser = cachedFeed?.openEntriesInBrowser ?: false,
                            blockedWords = cachedFeed?.blockedWords ?: "",
                            showPreviewImages = cachedFeed?.showPreviewImages,
                        )
                    )
                }
            }
        }
    }
}