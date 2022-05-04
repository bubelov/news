package feeds

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.EntryQueries
import db.Feed
import db.FeedQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

class FeedsRepository(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val api: NewsApi,
) {

    suspend fun insertOrReplace(feed: Feed) {
        withContext(Dispatchers.Default) {
            feedQueries.insertOrReplace(feed)
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Feed {
        return withContext(Dispatchers.Default) {
            val feed = api.addFeed(url).getOrThrow()
            feedQueries.insertOrReplace(feed)
            feed
        }
    }

    fun selectAll(): Flow<List<Feed>> {
        return feedQueries.selectAll().asFlow().mapToList()
    }

    fun selectById(id: String): Flow<Feed?> {
        return feedQueries.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun updateTitle(feedId: String, newTitle: String) {
        withContext(Dispatchers.Default) {
            val feed = feedQueries.selectById(feedId).executeAsOneOrNull()
                ?: throw Exception("Cannot find feed $feedId in cache")
            val trimmedNewTitle = newTitle.trim()
            api.updateFeedTitle(feedId, trimmedNewTitle)
            feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }
    }

    suspend fun deleteById(id: String) {
        withContext(Dispatchers.Default) {
            api.deleteFeed(id)

            feedQueries.transaction {
                feedQueries.deleteById(id)
                entryQueries.deleteByFeedId(id)
            }
        }
    }

    suspend fun sync() {
        withContext(Dispatchers.Default) {
            val newFeeds = api.getFeeds().sortedBy { it.id }
            val cachedFeeds = selectAll().first().sortedBy { it.id }

            feedQueries.transaction {
                feedQueries.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.id }

                    feedQueries.insertOrReplace(
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