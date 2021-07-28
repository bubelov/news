package api

import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import org.joda.time.Instant
import java.net.URL

interface NewsApi {

    suspend fun addFeed(url: URL): Feed

    suspend fun getFeeds(): List<Feed>

    suspend fun updateFeedTitle(feedId: String, newTitle: String)

    suspend fun deleteFeed(feedId: String)

    suspend fun getAllEntries(): Flow<GetEntriesResult>

    suspend fun getBookmarkedEntries(): List<Entry>

    suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry>

    suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean)

    suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean)
}