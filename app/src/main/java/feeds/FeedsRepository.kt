package feeds

import api.*
import db.FeedQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedsRepository(
    private val api: NewsApi,
    private val db: FeedQueries,
) {

    suspend fun insertByUrl(url: String) = withContext(Dispatchers.IO) {
        db.insertOrReplace(api.addFeed(url))
    }

    suspend fun selectAll() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList()
    }

    suspend fun selectById(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).executeAsOneOrNull()
    }

    suspend fun updateTitle(feedId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val feed = selectById(feedId) ?: throw Exception("Cannot find feed $feedId in cache")
        val trimmedNewTitle = newTitle.trim()
        api.updateFeedTitle(feedId, trimmedNewTitle)
        db.insertOrReplace(feed.copy(title = trimmedNewTitle))
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        api.deleteFeed(id)
        db.deleteById(id)
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        val newFeeds = api.getFeeds()
        val cachedFeeds = selectAll()

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