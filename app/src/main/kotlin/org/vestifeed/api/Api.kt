package org.vestifeed.api

import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.Link
import java.time.OffsetDateTime

interface Api {
    data class AddFeedResult(
        val feed: Feed,
        val feedLinks: List<Link>,
        val entries: List<Pair<Entry, List<Link>>>,
    )

    suspend fun addFeed(url: HttpUrl): AddFeedResult

    suspend fun getFeeds(): List<Feed>

    suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    ): Result<Unit>

    suspend fun deleteFeed(feedId: String): Result<Unit>

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>>

    suspend fun markEntriesAsRead(
        entriesIds: List<String>,
        read: Boolean,
    )

    suspend fun markEntriesAsBookmarked(
        entries: List<EntryQueries.EntryWithoutContent>,
        bookmarked: Boolean,
    )
}