package co.appreactor.news.feeds

import co.appreactor.news.api.*
import co.appreactor.news.db.FeedQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FeedsRepository(
    private val feedQueries: FeedQueries,
    private val newsApi: NewsApi,
) {

    suspend fun add(url: String) = withContext(Dispatchers.IO) {
        val feed = newsApi.addFeed(url)
        feedQueries.insertOrReplace(feed)
    }

    suspend fun getAll() = withContext(Dispatchers.IO) {
        feedQueries.selectAll().asFlow().mapToList()
    }

    suspend fun get(id: String) = withContext(Dispatchers.IO) {
        feedQueries.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun rename(feedId: String, newTitle: String) = withContext(Dispatchers.IO) {
        newsApi.updateFeedTitle(feedId, newTitle)
        val feed = get(feedId).first() ?: return@withContext
        feedQueries.insertOrReplace(feed.copy(title = newTitle))
    }

    suspend fun delete(feedId: String) = withContext(Dispatchers.IO) {
        newsApi.deleteFeed(feedId)
        feedQueries.deleteById(feedId)
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        val newFeeds = newsApi.getFeeds()
        val cachedFeeds = getAll().first()

        if (newFeeds.sortedBy { it.id } != cachedFeeds.sortedBy { it.id }) {
            feedQueries.transaction {
                feedQueries.deleteAll()

                newFeeds.forEach {
                    feedQueries.insertOrReplace(it)
                }
            }
        }
    }
}