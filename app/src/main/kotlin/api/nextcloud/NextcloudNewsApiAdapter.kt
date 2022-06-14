package api.nextcloud

import api.NewsApi
import db.Entry
import db.EntryWithoutContent
import db.Feed
import db.Link
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.OffsetDateTime

class NextcloudNewsApiAdapter(
    private val api: NextcloudNewsApi,
) : NewsApi {

    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
        return runCatching {
            api.postFeed(PostFeedArgs(url.toString(), 0)).feeds.single().toFeed()
                ?: throw Exception("Invalid server response")
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return api.getFeeds().feeds.mapNotNull { it.toFeed() }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {
        api.putFeedRename(feedId.toLong(), PutFeedRenameArgs(newTitle))
    }

    override suspend fun deleteFeed(feedId: String) {
        api.deleteFeed(feedId.toLong())
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> = flow {
        var totalFetched = 0L
        val currentBatch = mutableSetOf<ItemJson>()
        val batchSize = 250L
        var oldestEntryId = 0L

        while (true) {
            val response = try {
                api.getAllItems(
                    getRead = includeReadEntries,
                    batchSize = batchSize,
                    offset = oldestEntryId,
                )
            } catch (e: Exception) {
                val message = if (e.message == "code < 400: 302") {
                    "Can not load entries. Make sure you have News app installed on your Nextcloud server."
                } else {
                    e.message
                }

                throw Exception(message, e)
            }

            val entries = response.items
            currentBatch += entries
            totalFetched += currentBatch.size
            emit(currentBatch.mapNotNull { it.toEntry() })

            if (currentBatch.size < batchSize) {
                break
            } else {
                oldestEntryId =
                    currentBatch.minOfOrNull { it.id ?: Long.MAX_VALUE }?.toLong() ?: 0L
                currentBatch.clear()
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        val lastModified = maxEntryUpdated ?: lastSync!!
        return api.getNewAndUpdatedItems(lastModified.toEpochSecond() + 1).items.mapNotNull { it.toEntry() }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        val ids = entriesIds.map { it.toLong() }

        if (read) {
            api.putRead(PutReadArgs(ids))
        } else {
            api.putUnread(PutReadArgs(ids))
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        val args = PutStarredArgs(entries.map { PutStarredArgsItem(it.feedId.toLong(), it.guidHash) })

        if (bookmarked) {
            api.putStarred(args)
        } else {
            api.putUnstarred(args)
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        val feedId = id?.toString() ?: return null

        val selfLink = Link(
            feedId = feedId,
            entryId = null,
            href = url!!.toHttpUrl(),
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
            href = link!!.toHttpUrl(),
            rel = "alternate",
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        return Feed(
            id = feedId,
            title = title ?: "Untitled",
            links = listOf(selfLink, alternateLink),
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = null,
        )
    }

    private fun ItemJson.toEntry(): Entry? {
        if (id == null) return null
        if (pubDate == null) return null
        if (lastModified == null) return null
        if (unread == null) return null
        if (starred == null) return null

        val published = Instant.ofEpochSecond(pubDate).toString()
        val updated = Instant.ofEpochSecond(lastModified).toString()

        val links = mutableListOf<Link>()

        links += Link(
            feedId = null,
            entryId = id.toString(),
            href = url!!.toHttpUrl(),
            rel = "alternate",
            type = "",
            hreflang = "",
            title = "",
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        if (!enclosureLink.isNullOrBlank()) {
            links += Link(
                feedId = null,
                entryId = id.toString(),
                href = enclosureLink.toHttpUrl(),
                rel = "enclosure",
                type = enclosureMime ?: "",
                hreflang = "",
                title = "",
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Entry(
            contentType = "html",
            contentSrc = "",
            contentText = body ?: "",
            links = links,
            summary = "",
            id = id.toString(),
            feedId = feedId?.toString() ?: "",
            title = title ?: "Untitled",
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = author ?: "",

            read = !unread,
            readSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = guidHash ?: return null,
            commentsUrl = "",

            ogImageChecked = false,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
        )
    }
}