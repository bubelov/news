package org.vestifeed.api.miniflux

import android.util.Log
import kotlinx.coroutines.Dispatchers
import org.vestifeed.api.Api
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.Link
import org.vestifeed.parser.AtomLinkRel
import java.time.OffsetDateTime

class MinifluxApiAdapter(
    private val api: MinifluxApi,
) : Api {

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
        return runCatching {
            val categories = api.getCategories()

            if (categories.isEmpty()) {
                return Result.failure(Exception("You have no categories"))
            }

            // Catch-all category always has the lowest id
            val category = categories.minByOrNull { it.id }!!

            val response = api.postFeed(
                PostFeedArgs(
                    feed_url = url.toString(),
                    category_id = category.id,
                )
            )

            Pair(api.getFeed(response.feed_id).toFeed()!!, emptyList())
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return withContext(Dispatchers.IO) {
            api.getFeeds().map { it.toFeed()!! }
        }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return runCatching { api.putFeed(feedId.toLong(), PutFeedArgs(newTitle)) }
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return runCatching { api.deleteFeed(feedId.toLong()) }
    }

    override suspend fun getEntries(
        includeReadEntries: Boolean,
    ): Flow<List<Entry>> {
        return flow {
            var totalFetched = 0L
            val currentBatch = mutableSetOf<EntryJson>()
            val batchSize = 10L
            var oldestEntryId = Long.MAX_VALUE

            while (true) {
                val entries = withContext(Dispatchers.IO) {
                    api.getEntriesBeforeEntry(
                        status = if (includeReadEntries) "" else "unread",
                        entryId = oldestEntryId,
                        limit = batchSize,
                    )
                }

                currentBatch += entries.entries
                totalFetched += currentBatch.size
                val mappedCurrentBatch =
                    withContext(Dispatchers.IO) { currentBatch.map { it.toEntry() } }
                emit(mappedCurrentBatch)

                if (currentBatch.size < batchSize) {
                    break
                } else {
                    oldestEntryId = currentBatch.minOfOrNull { it.id } ?: 0L
                    currentBatch.clear()
                }
            }

            val starredEntries = withContext(Dispatchers.IO) { api.getStarredEntries() }

            if (starredEntries.entries.isNotEmpty()) {
                currentBatch += starredEntries.entries
                val mappedCurrentBatch =
                    withContext(Dispatchers.IO) { currentBatch.map { it.toEntry() } }
                emit(mappedCurrentBatch)
                currentBatch.clear()
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        val changedAfter = lastSync?.toEpochSecond() ?: 0L
        val res = withContext(Dispatchers.IO) {
            api.getEntriesChangedAfter(
                changedAfter = changedAfter,
                limit = 0,
            )
        }
        return withContext(Dispatchers.IO) {
            res.entries.map { it.toEntry() }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        withContext(Dispatchers.IO) {
            api.putEntryStatus(
                PutStatusArgs(
                    entry_ids = entriesIds.map { it.toLong() },
                    status = if (read) "read" else "unread",
                )
            )
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryQueries.EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            entries.forEach { api.putEntryBookmark(it.id.toLong()) }
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        val feedId = id?.toString() ?: return null

        val selfLink = Link(
            feedId = feedId,
            entryId = null,
            href = feed_url.toHttpUrl(),
            rel = AtomLinkRel.Self,
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        val alternateLink = try {
            Link(
                feedId = feedId,
                entryId = null,
                href = site_url.toHttpUrl(),
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        } catch (e: Exception) {
            Log.d("MinifluxApiAdapter", "Failed to parse alternate link for feed $feed_url", e)
            null
        }

        return Feed(
            id = feedId,
            links = listOfNotNull(selfLink, alternateLink),
            title = title,
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
    }

    private fun EntryJson.toEntry(): Entry {
        val links = mutableListOf<Link>()

        links += Link(
            feedId = null,
            entryId = id.toString(),
            href = url.toHttpUrl(),
            rel = AtomLinkRel.Alternate,
            type = "text/html",
            hreflang = "",
            title = "",
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        enclosures?.forEach {
            links += Link(
                feedId = null,
                entryId = id.toString(),
                href = it.url.toHttpUrl(),
                rel = AtomLinkRel.Enclosure,
                type = it.mime_type,
                hreflang = "",
                title = "",
                length = it.size,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Entry(
            contentType = "html",
            contentSrc = "",
            contentText = content,
            links = links,
            summary = null,
            id = id.toString(),
            feedId = feed_id.toString(),
            title = title,
            published = OffsetDateTime.parse(published_at),
            updated = OffsetDateTime.parse(changed_at),
            authorName = author,
            extRead = status == "read",
            extReadSynced = true,
            extBookmarked = starred,
            extBookmarkedSynced = true,
            extCommentsUrl = comments_url,
            extOpenGraphImageChecked = false,
            extOpenGraphImageUrl = "",
            extOpenGraphImageWidth = 0,
            extOpenGraphImageHeight = 0,
        )
    }
}