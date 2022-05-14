package feeds

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Database
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.koin.core.annotation.Single

@Single
class FeedsRepository(
    private val db: Database,
    private val api: NewsApi,
) {

    suspend fun insertOrReplace(feed: Feed) {
        withContext(Dispatchers.Default) {
            db.feedQueries.insertOrReplace(feed)
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Feed {
        return withContext(Dispatchers.Default) {
            val feed = api.addFeed(url).getOrThrow()

            db.transaction {
                db.feedQueries.insertOrReplace(feed.first)
                feed.second.forEach { db.linkQueries.insertOrReplace(it) }
            }

            feed.first
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
            val newFeeds = api.getFeeds().sortedBy { it.first.id }
            val cachedFeeds = selectAll().first().sortedBy { it.id }

            db.transaction {
                db.feedQueries.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.first.id }

                    db.feedQueries.insertOrReplace(
                        feed.first.copy(
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