package org.vestifeed.api

import org.vestifeed.db.Entry
import org.vestifeed.db.EntryWithoutContent
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.table.Feed
import java.time.OffsetDateTime

interface Api {

    suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>>

    suspend fun getFeeds(): List<Feed>

    suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    ): Result<Unit>

    suspend fun deleteFeed(feedId: String): Result<Unit>

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