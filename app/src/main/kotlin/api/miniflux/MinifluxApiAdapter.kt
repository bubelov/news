package api.miniflux

import api.NewsApi
import db.Entry
import db.EntryWithoutContent
import db.Feed
import db.Link
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime

class MinifluxApiAdapter(
    private val api: MinifluxApi,
) : NewsApi {

    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
        return runCatching {
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

            api.getFeed(response.feed_id).toFeed()!!
        }
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
            val entries = api.getEntriesBeforeEntry(
                status = if (includeReadEntries) "" else "unread",
                entryId = oldestEntryId,
                limit = batchSize,
            )

            currentBatch += entries.entries
            totalFetched += currentBatch.size
            emit(currentBatch.map { it.toEntry() })

            if (currentBatch.size < batchSize) {
                break
            } else {
                oldestEntryId = currentBatch.minOfOrNull { it.id }?.toLong() ?: 0L
                currentBatch.clear()
            }
        }

        val starredEntries = api.getStarredEntries()

        if (starredEntries.entries.isNotEmpty()) {
            currentBatch += starredEntries.entries
            totalFetched += currentBatch.size
            emit(currentBatch.map { it.toEntry() })
            currentBatch.clear()
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
        ).entries.map { it.toEntry() }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        if (read) {
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

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        entries.forEach {
            api.putEntryBookmark(it.id.toLong())
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        val feedId = id?.toString() ?: return null

        val selfLink = Link(
            feedId = feedId,
            entryId = null,
            href = feed_url.toHttpUrl(),
            rel = "self",
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        val alternateLink = Link(
            feedId = feedId,
            entryId = null,
            href = site_url.toHttpUrl(),
            rel = "alternate",
            type = "text/html",
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        return Feed(
            id = feedId,
            title = title,
            links = listOf(selfLink, alternateLink),
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = null,
        )
    }

    private fun EntryJson.toEntry(): Entry {
        val links = mutableListOf<Link>()

        links += Link(
            feedId = null,
            entryId = id.toString(),
            href = url.toHttpUrl(),
            rel = "alternate",
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
                rel = "enclosure",
                type = it.mime_type,
                hreflang = "",
                title = "",
                length = it.size,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Entry(
            contentType = null,
            contentSrc = null,
            contentText = null,
            links = links,
            summary = null,
            id = id.toString(),
            feedId = feed_id.toString(),
            title = title,
            published = OffsetDateTime.parse(published_at),
            updated = OffsetDateTime.parse(changed_at),
            authorName = author,

            read = status == "read",
            readSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = "",
            commentsUrl = comments_url,

            ogImageChecked = false,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
        )
    }
}