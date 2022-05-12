package api

import db.Entry
import db.EntryWithoutContent
import db.Feed
import db.Link
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import java.time.OffsetDateTime

interface NewsApi {

    suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Link>>>

    suspend fun getFeeds(): List<Pair<Feed, List<Link>>>

    suspend fun updateFeedTitle(feedId: String, newTitle: String)

    suspend fun deleteFeed(feedId: String)

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>>

    suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean)

    suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutContent>, bookmarked: Boolean)
}