package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsFeedQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}