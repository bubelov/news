package api.miniflux

import api.NewsApi
import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime

class MinifluxApiAdapter(
    private val api: MinifluxApi,
) : NewsApi {

    override suspend fun addFeed(url: URL): Feed {
        val categories = api.getCategories()

        val category = categories.find { it.title.equals("All", ignoreCase = true) }
            ?: categories.firstOrNull()
            ?: throw Exception("You have no categories to place this feed into")

        val response = api.postFeed(
            PostFeedArgs(
                feed_url = url.toString(),
                category_id = category.id,
            )
        )

        return api.getFeed(response.feed_id).toFeed()!!
    }

    override suspend fun getFeeds(): List<Feed> {
        return api.getFeeds().mapNotNull { it.toFeed() }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {
        api.putFeed(feedId.toLong(), PutFeedArgs(newTitle))
    }

    override suspend fun deleteFeed(feedId: String) {
        api.deleteFeed(feedId.toLong())
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> = flow {
        var totalFetched = 0L
        val currentBatch = mutableSetOf<EntryJson>()
        val batchSize = 250L
        var oldestEntryId = Long.MAX_VALUE

        while (true) {
            Timber.d("Oldest entry ID: $oldestEntryId")

            val entries = api.getEntriesBeforeEntry(
                status = if (includeReadEntries) "" else "unread",
                entryId = oldestEntryId,
                limit = batchSize,
            )

            Timber.d("Got ${entries.entries.size} entries")
            currentBatch += entries.entries
            totalFetched += currentBatch.size
            Timber.d("Fetched $totalFetched entries so far")
            emit(currentBatch.mapNotNull { it.toEntry() })

            if (currentBatch.size < batchSize) {
                break
            } else {
                oldestEntryId = currentBatch.minOfOrNull { it.id }?.toLong() ?: 0L
                currentBatch.clear()
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        return api.getEntriesAfterEntry(
            afterEntryId = maxEntryId?.toLong() ?: 0,
            limit = 0,
        ).entries.mapNotNull { it.toEntry() }
    }

    override suspend fun markEntriesAsOpened(entriesIds: List<String>, opened: Boolean) {
        if (opened) {
            api.putEntryStatus(
                PutStatusArgs(
                    entry_ids = entriesIds.map { it.toLong() },
                    status = "read"
                )
            )
        } else {
            api.putEntryStatus(
                PutStatusArgs(
                    entry_ids = entriesIds.map { it.toLong() },
                    status = "unread"
                )
            )
        }
    }

    override suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
        entries.forEach {
            api.putEntryBookmark(it.id.toLong())
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        return Feed(
            id = id?.toString() ?: return null,
            title = title,
            selfLink = feed_url,
            alternateLink = site_url,
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = null,
        )
    }

    private fun EntryJson.toEntry(): Entry? {
        val firstEnclosure = enclosures?.firstOrNull()

        return Entry(
            id = id.toString(),
            feedId = feed_id.toString(),
            title = title,
            link = url.replace("http://", "https://"),
            published = published_at,
            updated = changed_at,
            authorName = author,
            content = content,
            enclosureLink = firstEnclosure?.url ?: "",
            enclosureLinkType = firstEnclosure?.mime_type ?: "",

            opened = status == "read",
            openedSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = "",
        )
    }
}