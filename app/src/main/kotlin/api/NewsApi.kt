package api

import db.Entry
import db.EntryWithoutContent
import db.Feed
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import java.time.OffsetDateTime

interface NewsApi {

    suspend fun addFeed(url: HttpUrl): Result<Feed>

    suspend fun getFeeds(): Result<List<Feed>>

    suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    )

    suspend fun deleteFeed(feedId: String)

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry>

    suspend fun markEntriesAsRead(
        entriesIds: List<String>,
        read: Boolean,
    )

    suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    )
}