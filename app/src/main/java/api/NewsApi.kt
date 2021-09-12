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

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: Instant?,
        lastSync: Instant?,
    ): List<Entry>

    suspend fun markEntriesAsOpened(entriesIds: List<String>, opened: Boolean)

    suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean)
}