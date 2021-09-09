package api.miniflux

import api.GetEntriesResult
import api.NewsApi
import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.joda.time.Instant
import retrofit2.HttpException
import timber.log.Timber
import java.net.URL

class MinifluxApiAdapter(
    private val api: MinifluxApi,
) : NewsApi {

    override suspend fun addFeed(url: URL): Feed {
        try {
            val response = api.postFeed(PostFeedArgs(feed_url = url.toString(), category_id = 1))
            return api.getFeed(response.feed_id).toFeed()!!
        } catch (e: HttpException) {
            throw MinifluxApiException.from(e)
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

    override suspend fun getAllEntries(): Flow<GetEntriesResult> = flow {
        emit(GetEntriesResult.Loading(0, emptyList()))

        var totalFetched = 0L
        val currentBatch = mutableSetOf<EntryJson>()
        val batchSize = 250L
        var oldestEntryId = Long.MAX_VALUE

        while (true) {
            Timber.d("Oldest entry ID: $oldestEntryId")

            val entries = api.getEntriesBeforeEntry(
                entryId = oldestEntryId,
                limit = batchSize,
            )
            Timber.d("Got ${entries.entries.size} entries")
            currentBatch += entries.entries
            totalFetched += currentBatch.size
            Timber.d("Fetched $totalFetched entries so far")
            val validEntries = currentBatch.mapNotNull { it.toEntry() }
            emit(GetEntriesResult.Loading(totalFetched, validEntries))

            if (currentBatch.size < batchSize) {
                break
            } else {
                oldestEntryId = currentBatch.minOfOrNull { it.id ?: Long.MAX_VALUE }?.toLong() ?: 0L
                currentBatch.clear()
            }
        }

        emit(GetEntriesResult.Success)
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        return api.getEntries(since.millis / 1000).entries.mapNotNull { it.toEntry() }
    }

    override suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean) {
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

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
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
        if (id == null) return null
        if (created_at == null) return null
        if (published_at == null) return null
        if (changed_at == null) return null
        if (status == null) return null
        if (starred == null) return null

        return Entry(
            id = id.toString(),
            feedId = feed_id?.toString() ?: "",
            title = title ?: "Untitled",
            link = url?.replace("http://", "https://") ?: "",
            published = published_at,
            updated = changed_at,
            authorName = author ?: "",
            content = content ?: "No content",
            enclosureLink = "",
            enclosureLinkType = "",

            opened = status == "read",
            openedSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = "",
        )
    }
}