package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.api.FeedJson
import co.appreactor.nextcloud.news.api.NewsApi
import co.appreactor.nextcloud.news.api.PostFeedArgs
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedQueries
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FeedsRepository(
    private val db: FeedQueries,
    private val api: NewsApi,
    private val feedItemsRepository: FeedItemsRepository
) {

    suspend fun create(url: String) = withContext(Dispatchers.IO) {
        val response = kotlin.runCatching {
            api.postFeed(PostFeedArgs(url, 0)).execute().body()!!
        }.getOrThrow()

        val feed = response.feeds.single().toFeed()

        if (feed != null) {
            db.insertOrReplace(feed)
        }
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun byId(id: Long) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneNotNull()
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        val response = api.deleteFeed(id).execute()

        if (response.isSuccessful) {
            db.transaction {
                db.deleteById(id)
                feedItemsRepository.deleteByFeedId(id)
            }
        } else {
            throw Exception("HTTPS request failed with error code ${response.code()}")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    suspend fun reloadFromApiIfNoData() = withContext(Dispatchers.IO) {
        if (db.selectCount().executeAsOne() == 0L) {
            reloadFromApi()
        }
    }

    suspend fun reloadFromApi() = withContext(Dispatchers.IO) {
        val cachedFeeds = all().first().sortedBy { it.id }
        val newFeeds = api.getFeeds().execute().body()!!.feeds.sortedBy { it.id }

        if (newFeeds != cachedFeeds) {
            db.transaction {
                db.deleteAll()

                newFeeds.mapNotNull { it.toFeed() }.forEach {
                    db.insertOrReplace(it)
                }
            }
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        return Feed(
            id = id ?: return null,
            url = url ?: "",
            title = title ?: "Untitled",
            faviconLink = faviconLink ?: "",
            link = link ?: "",
            updateErrorCount = updateErrorCount ?: 0,
            lastUpdateError = lastUpdateError ?: ""
        )
    }
}