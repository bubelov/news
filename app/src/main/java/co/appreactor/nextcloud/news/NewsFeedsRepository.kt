package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsFeedQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class NewsFeedsRepository(
    private val cache: NewsFeedQueries,
    private val api: NewsApi
) {

    suspend fun all() = withContext(Dispatchers.IO) {
        cache.findAll().executeAsList()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.deleteAll()
    }

    suspend fun reloadFromApiIfNoData() = withContext(Dispatchers.IO) {
        if (cache.count().executeAsOne() == 0L) {
            reloadFromApi()
        }
    }

    suspend fun reloadFromApi() = withContext(Dispatchers.IO) {
        Timber.d("Reloading from API")
        val feeds = api.getFeeds().execute().body()!!.feeds
        Timber.d("Got ${feeds.size} feeds")

        cache.transaction {
            cache.deleteAll()

            feeds.forEach {
                cache.insertOrReplace(it)
            }
        }

        Timber.d("Finished reloading from API")
    }
}