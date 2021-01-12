package feeds

import api.*
import db.FeedQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FeedsRepository(
    private val db: FeedQueries,
    private val api: NewsApi,
) {

    suspend fun add(url: String) = withContext(Dispatchers.IO) {
        val feed = api.addFeed(url)
        db.insertOrReplace(feed)
    }

    suspend fun getAll() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun get(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun getCount() = withContext(Dispatchers.IO) {
        db.selectCount().asFlow().mapToOne()
    }

    suspend fun updateTitle(feedId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val feed = db.selectById(feedId).executeAsOneOrNull()
            ?: throw Exception("Cannot find feed $feedId in cache")

        val trimmedNewTitle = newTitle.trim()
        api.updateFeedTitle(feedId, trimmedNewTitle)
        db.insertOrReplace(feed.copy(title = trimmedNewTitle))
    }

    suspend fun delete(feedId: String) = withContext(Dispatchers.IO) {
        api.deleteFeed(feedId)
        db.deleteById(feedId)
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        val newFeeds = api.getFeeds()
        val cachedFeeds = getAll().first()

        if (newFeeds.sortedBy { it.id } != cachedFeeds.sortedBy { it.id }) {
            db.transaction {
                db.deleteAll()

                newFeeds.forEach {
                    db.insertOrReplace(it)
                }
            }
        }
    }
}