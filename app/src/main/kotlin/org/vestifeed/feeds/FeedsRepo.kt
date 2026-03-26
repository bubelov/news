package org.vestifeed.feeds

import android.util.Log
import org.vestifeed.api.Api
import org.vestifeed.db.Db
import org.vestifeed.db.Entry
import org.vestifeed.db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

class FeedsRepo(
    private val api: Api,
    private val db: Db,
) {
    private val _feedsFlow = MutableStateFlow<List<Feed>>(emptyList())
    private val _feedsWithUnreadFlow = MutableStateFlow<List<org.vestifeed.db.SelectAllWithUnreadEntryCount>>(emptyList())

    private fun refreshFlows() {
        _feedsFlow.value = db.feedQueries.selectAll()
        _feedsWithUnreadFlow.value = db.feedQueries.selectAllWithUnreadEntryCount()
    }

    suspend fun insertOrReplace(feed: Feed) {
        withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feed)
            refreshFlows()
        }
    }

    suspend fun insertByUrl(url: HttpUrl): Pair<Feed, List<Entry>> {
        val feedWithEntries = api.addFeed(url).getOrThrow()

        return withContext(Dispatchers.IO) {
            db.feedQueries.insertOrReplace(feedWithEntries.first)
            refreshFlows()
            feedWithEntries
        }
    }

    fun selectAll(): Flow<List<Feed>> = _feedsFlow.asStateFlow()

    fun refresh() {
        refreshFlows()
    }

    fun selectAllWithUnreadEntryCount(): Flow<List<org.vestifeed.db.SelectAllWithUnreadEntryCount>> = _feedsWithUnreadFlow.asStateFlow()

    fun selectById(id: String): Flow<Feed?> {
        return kotlinx.coroutines.flow.flowOf(db.feedQueries.selectById(id))
    }

    fun selectLinks(): Flow<List<List<org.vestifeed.db.Link>>> {
        return kotlinx.coroutines.flow.flowOf(db.feedQueries.selectLinks())
    }

    suspend fun updateTitle(feedId: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            val feed = db.feedQueries.selectById(feedId)
                ?: throw Exception("Cannot find feed $feedId in cache")
            val trimmedNewTitle = newTitle.trim()
            api.updateFeedTitle(feedId, trimmedNewTitle)
            db.feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
            refreshFlows()
        }
    }

    suspend fun deleteById(id: String) {
        api.deleteFeed(id)

        withContext(Dispatchers.IO) {
            db.transaction {
                db.feedQueries.deleteById(id)
                db.entryQueries.deleteByFeedId(id)
            }
            refreshFlows()
        }
    }

    suspend fun sync() {
        withContext(Dispatchers.IO) {
            Log.d("sync", "requesting feeds from api")
            val newFeeds = api.getFeeds().getOrThrow().sortedBy { it.id }
            Log.d("sync", "got ${newFeeds.size} feeds")
            Log.d("sync", "getting cached feeds")
            val cachedFeeds = _feedsFlow.value.sortedBy { it.id }
            Log.d("sync", "got ${cachedFeeds.size} cached feeds")
            Log.d("sync", "preparing write transaction")
            db.transaction {
                db.feedQueries.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.id }

                    db.feedQueries.insertOrReplace(
                        feed.copy(
                            extOpenEntriesInBrowser = cachedFeed?.extOpenEntriesInBrowser ?: false,
                            extBlockedWords = cachedFeed?.extBlockedWords ?: "",
                            extShowPreviewImages = cachedFeed?.extShowPreviewImages,
                        )
                    )
                }
            }
            Log.d("sync", "finished write transaction")
            Log.d("sync", "preparing to refresh flows")
            refreshFlows()
            Log.d("sync", "done refreshing flows")
        }
        Log.d("sync", "returning")
    }

//    init {
//        refreshFlows()
//    }
}
