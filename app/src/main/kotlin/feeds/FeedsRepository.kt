package feeds

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
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

    suspend fun insertByFeedUrl(url: HttpUrl, title: String? = null) {
        withContext(Dispatchers.Default) {
            var feed = withContext(Dispatchers.IO) {
                api.addFeed(url).getOrThrow()
            }

            if (!title.isNullOrBlank()) {
                feed = feed.copy(title = title)
            }

            feedQueries.insertOrReplace(feed)
        }
    }

    fun selectAll(): Flow<List<Feed>> {
        return feedQueries.selectAll().asFlow().mapToList()
    }

    suspend fun selectById(id: String): Feed? {
        return withContext(Dispatchers.Default) {
            feedQueries.selectById(id).executeAsOneOrNull()
        }
    }

    suspend fun updateTitle(feedId: String, newTitle: String) {
        val feed = selectById(feedId) ?: throw Exception("Cannot find feed $feedId in cache")
        val trimmedNewTitle = newTitle.trim()

        withContext(Dispatchers.IO) {
            api.updateFeedTitle(feedId, trimmedNewTitle)
        }

        withContext(Dispatchers.Default) {
            feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }
    }

    suspend fun deleteById(id: String) {
        withContext(Dispatchers.IO) {
            api.deleteFeed(id)
        }

        withContext(Dispatchers.Default) {
            feedQueries.transaction {
                feedQueries.deleteById(id)
                entryQueries.deleteByFeedId(id)
            }
        }
    }

    suspend fun sync() {
        val newFeeds = withContext(Dispatchers.IO) {
            api.getFeeds().sortedBy { it.id }
        }

        withContext(Dispatchers.Default) {
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