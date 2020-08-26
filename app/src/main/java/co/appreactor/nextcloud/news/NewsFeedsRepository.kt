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
        if (cache.findAll().executeAsList().isEmpty()) {
            val feeds = api.getFeeds()

            cache.transaction {
                feeds.feeds.forEach {
                    cache.insertOrReplace(it)
                }
            }
        }

        cache.findAll().executeAsList()
    }

    suspend fun reloadFromApi() = withContext(Dispatchers.IO) {
        Timber.d("Reloading from API")
        val feeds = api.getFeeds()
        Timber.d("Got ${feeds.feeds.size} feeds")

        cache.transaction {
            cache.deleteAll()

            feeds.feeds.forEach {
                cache.insertOrReplace(it)
            }
        }

        Timber.d("Finished reloading from API")
    }
}