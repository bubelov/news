package feeds

import api.Api
import db.Db
import db.Entry
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.koin.core.annotation.Single

@Single
class FeedsRepo(
    private val api: Api,
    private val db: Db,
) {
    private val _feedsFlow = MutableStateFlow<List<Feed>>(emptyList())
    private val _feedsWithUnreadFlow = MutableStateFlow<List<db.SelectAllWithUnreadEntryCount>>(emptyList())

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

    fun selectAllWithUnreadEntryCount(): Flow<List<db.SelectAllWithUnreadEntryCount>> = _feedsWithUnreadFlow.asStateFlow()

    fun selectById(id: String): Flow<Feed?> {
        return kotlinx.coroutines.flow.flowOf(db.feedQueries.selectById(id))
    }

    fun selectLinks(): Flow<List<List<db.Link>>> {
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
            val newFeeds = api.getFeeds().getOrThrow().sortedBy { it.id }
            val cachedFeeds = _feedsFlow.value.sortedBy { it.id }

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
            refreshFlows()
        }
    }

    init {
        refreshFlows()
    }
}
