package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.api.NewsApi
import co.appreactor.nextcloud.news.db.FeedQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

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
        Timber.d("Reloading from API")
        val feeds = api.getFeeds().execute().body()!!.feeds
        Timber.d("Got ${feeds.size} feeds")

        db.transaction {
            db.deleteAll()

            feeds.forEach {
                db.insertOrReplace(it)
            }
        }

        Timber.d("Finished reloading from API")
    }
}