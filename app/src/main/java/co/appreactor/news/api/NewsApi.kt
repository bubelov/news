package co.appreactor.news.api

import co.appreactor.news.db.Entry
import co.appreactor.news.db.EntryWithoutSummary
import co.appreactor.news.db.Feed
import kotlinx.coroutines.flow.Flow
import org.joda.time.Instant

interface NewsApi {

    suspend fun addFeed(uri: String): Feed

    suspend fun getFeeds(): List<Feed>

    suspend fun updateFeedTitle(feedId: String, newTitle: String)

    suspend fun deleteFeed(feedId: String)

    suspend fun getNotViewedEntries(): Flow<GetNotViewedEntriesResult>

    suspend fun getBookmarkedEntries(): List<Entry>

    suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry>

    suspend fun markAsViewed(entriesIds: List<String>, viewed: Boolean)

    suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean)
}