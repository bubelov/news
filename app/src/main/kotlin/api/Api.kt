package api

import db.Entry
import db.EntryWithoutContent
import db.Feed
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import java.time.OffsetDateTime

interface Api {

    suspend fun addFeed(url: HttpUrl): Result<Feed>

    suspend fun getFeeds(): Result<List<Feed>>

    suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    ): Result<Unit>

    suspend fun deleteFeed(feedId: String): Result<Unit>

    suspend fun getEntries(includeReadEntries: Boolean): Flow<Result<List<Entry>>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): Result<List<Entry>>

    suspend fun markEntriesAsRead(
        entriesIds: List<String>,
        read: Boolean,
    ): Result<Unit>

    suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ): Result<Unit>
}