package co.appreactor.news.feeds

import co.appreactor.news.api.FeedJson
import co.appreactor.news.api.NewsApi
import co.appreactor.news.api.PostFeedArgs
import co.appreactor.news.api.PutFeedRenameArgs
import co.appreactor.news.common.ConnectivityProbe
import co.appreactor.news.db.Feed
import co.appreactor.news.db.FeedQueries
import co.appreactor.news.entries.EntriesRepository
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.Response

@Suppress("BlockingMethodInNonBlockingContext")
class FeedsRepository(
    private val db: FeedQueries,
    private val api: NewsApi,
    private val entriesRepository: EntriesRepository,
    private val connectivityProbe: ConnectivityProbe,
) {

    suspend fun add(url: String) = withContext(Dispatchers.IO) {
        connectivityProbe.throwIfOffline()

        val response = api.postFeed(PostFeedArgs(url, 0)).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val feedJson = response.body()?.feeds?.single() ?: throw Exception("Can not parse server response")

        feedJson.toFeed()?.let {
            db.insertOrReplace(it)
        }
    }

    suspend fun getAll() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun get(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).asFlow().mapToOneOrNull()
    }

    suspend fun rename(feedId: String, newTitle: String) = withContext(Dispatchers.IO) {
        connectivityProbe.throwIfOffline()

        val response = api.putFeedRename(feedId.toLong(), PutFeedRenameArgs(newTitle)).execute()

        if (response.isSuccessful) {
            val feed = get(feedId).first()

            if (feed != null) {
                db.insertOrReplace(feed.copy(title = newTitle))
            }
        } else {
            throw response.toException()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val response = api.deleteFeed(id.toLong()).execute()

        if (response.isSuccessful) {
            db.transaction {
                db.deleteById(id)
                entriesRepository.deleteByFeedId(id)
            }
        } else {
            throw response.toException()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        val response = api.getFeeds().execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val newFeeds = response.body()?.feeds ?: throw Exception("Can not parse server response")
        val cachedFeeds = getAll().first()

        if (newFeeds.sortedBy { it.id } != cachedFeeds.sortedBy { it.id }) {
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
            id = id?.toString() ?: return null,
            title = title ?: "Untitled",
            link = url ?: "",
            alternateLink = link ?: "",
            alternateLinkType = "text/html",
        )
    }

    private fun Response<*>.toException() = Exception("HTTPS request failed with error code ${code()}")
}