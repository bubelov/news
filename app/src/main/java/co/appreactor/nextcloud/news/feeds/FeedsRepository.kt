package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.api.NewsApi
import co.appreactor.nextcloud.news.db.FeedQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FeedsRepository(
    private val db: FeedQueries,
    private val api: NewsApi
) {

    suspend fun all() = withContext(Dispatchers.IO) {
        db.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun byId(id: Long) = withContext(Dispatchers.IO) {
        db.findById(id).asFlow().map { it.executeAsOneOrNull() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    suspend fun reloadFromApiIfNoData() = withContext(Dispatchers.IO) {
        if (db.count().executeAsOne() == 0L) {
            reloadFromApi()
        }
    }

    suspend fun reloadFromApi() = withContext(Dispatchers.IO) {
        val cachedFeeds = all().first().sortedBy { it.id }
        val newFeeds = api.getFeeds().execute().body()!!.feeds.sortedBy { it.id }

        if (newFeeds != cachedFeeds) {
            db.transaction {
                db.deleteAll()

                newFeeds.forEach {
                    db.insertOrReplace(it)
                }
            }
        }
    }
}