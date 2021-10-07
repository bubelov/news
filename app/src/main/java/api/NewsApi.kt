package api

import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import java.net.URL
import java.time.OffsetDateTime

interface NewsApi {

    suspend fun addFeed(url: URL): Feed

    suspend fun getFeeds(): List<Feed>

    suspend fun updateFeedTitle(feedId: String, newTitle: String)

    suspend fun deleteFeed(feedId: String)

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry>

    suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean)

    suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean)
}