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
import retrofit2.Response
import java.time.Instant
import java.time.OffsetDateTime

class NextcloudNewsApiAdapter(
    private val api: NextcloudNewsApi,
) : NewsApi {

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Link>>> = runCatching {
        val response = api.postFeed(PostFeedArgs(url.toString(), 0)).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val feedJson =
            response.body()?.feeds?.single() ?: throw Exception("Can not parse server response")

        feedJson.toFeed() ?: throw Exception("Invalid server response")
    }

    override suspend fun getFeeds(): List<Pair<Feed, List<Link>>> {
        val response = api.getFeeds().execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        return response.body()?.feeds?.mapNotNull { it.toFeed() }
            ?: throw Exception("Can not parse server response")
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {
        val response = api.putFeedRename(feedId.toLong(), PutFeedRenameArgs(newTitle)).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }
    }

    override suspend fun deleteFeed(feedId: String) {
        val response = api.deleteFeed(feedId.toLong()).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>> = flow {
        var totalFetched = 0L
        val currentBatch = mutableSetOf<ItemJson>()
        val batchSize = 250L
        var oldestEntryId = 0L

        while (true) {
            val response = try {
                api.getAllItems(
                    getRead = includeReadEntries,
                    batchSize = batchSize,
                    offset = oldestEntryId
                ).execute()
            } catch (e: Exception) {
                val message = if (e.message == "code < 400: 302") {
                    "Can not load entries. Make sure you have News app installed on your Nextcloud server."
                } else {
                    e.message
                }

                throw Exception(message, e)
            }

            if (!response.isSuccessful) {
                throw response.toException()
            } else {
                val entries =
                    response.body()?.items ?: throw Exception("Can not parse server response")
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
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>> {
        val lastModified = maxEntryUpdated ?: lastSync!!

        val response = api.getNewAndUpdatedItems(lastModified.toEpochSecond() + 1).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        return response.body()?.items?.mapNotNull { it.toEntry() }
            ?: throw Exception("Can not parse server response")
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        val ids = entriesIds.map { it.toLong() }

        val response = if (read) {
            api.putRead(PutReadArgs(ids))
        } else {
            api.putUnread(PutReadArgs(ids))
        }.execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        val args =
            PutStarredArgs(entries.map { PutStarredArgsItem(it.feedId.toLong(), it.guidHash) })

        val response = if (bookmarked) {
            api.putStarred(args)
        } else {
            api.putUnstarred(args)
        }.execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }
    }

    private fun FeedJson.toFeed(): Pair<Feed, List<Link>>? {
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

        val feed = Feed(
            id = feedId,
            title = title ?: "Untitled",
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = null,
        )

        return Pair(feed, listOf(selfLink, alternateLink))
    }

    private fun ItemJson.toEntry(): Pair<Entry, List<Link>>? {
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

        val entry = Entry(
            contentType = "html",
            contentSrc = "",
            contentText = body ?: "",
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

        return Pair(entry, links)
    }

    private fun Response<*>.toException() =
        Exception("HTTPS request failed with error code ${code()}")
}