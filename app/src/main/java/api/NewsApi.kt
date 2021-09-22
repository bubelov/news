package api

import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

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

    suspend fun markEntriesAsOpened(entriesIds: List<String>, opened: Boolean)

    suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean)
}