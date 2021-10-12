package api.nextcloud

import api.NewsApi
import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import timber.log.Timber
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

class NextcloudNewsApiAdapter(
    private val api: NextcloudNewsApi,
) : NewsApi {

    override suspend fun addFeed(url: URL): Feed {
        val response = api.postFeed(PostFeedArgs(url.toString(), 0)).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val feedJson =
            response.body()?.feeds?.single() ?: throw Exception("Can not parse server response")

        return feedJson.toFeed() ?: throw Exception("Invalid server response")
    }

    override suspend fun getFeeds(): List<Feed> {
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

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> = flow {
        var totalFetched = 0L
        val currentBatch = mutableSetOf<ItemJson>()
        val batchSize = 250L
        var oldestEntryId = 0L

        while (true) {
            Timber.d("Oldest entry ID: $oldestEntryId")

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
                Timber.d("Got ${entries.size} entries")
                currentBatch += entries
                totalFetched += currentBatch.size
                Timber.d("Fetched $totalFetched entries so far")
                emit(currentBatch.mapNotNull { it.toEntry() })

                if (currentBatch.size < batchSize) {
                    break
                } else {
                    oldestEntryId = currentBatch.minOfOrNull { it.id ?: Long.MAX_VALUE }?.toLong() ?: 0L
                    currentBatch.clear()
                }
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
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

    override suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
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

    private fun FeedJson.toFeed(): Feed? {
        return Feed(
            id = id?.toString() ?: return null,
            title = title ?: "Untitled",
            selfLink = url ?: "",
            alternateLink = link ?: "",
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

        return Entry(
            id = id.toString(),
            feedId = feedId?.toString() ?: "",
            title = title ?: "Untitled",
            link = url?.replace("http://", "https://") ?: "",
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = author ?: "",
            content = body ?: "No content",
            enclosureLink = enclosureLink?.replace("http://", "https://") ?: "",
            enclosureLinkType = enclosureMime ?: "",

            read = !unread,
            readSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = guidHash ?: return null,
            commentsUrl = "",
        )
    }

    private fun Response<*>.toException() =
        Exception("HTTPS request failed with error code ${code()}")
}