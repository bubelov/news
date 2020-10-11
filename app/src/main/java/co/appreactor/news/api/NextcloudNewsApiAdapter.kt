package co.appreactor.news.api

import co.appreactor.news.db.Entry
import co.appreactor.news.db.EntryWithoutSummary
import co.appreactor.news.db.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.joda.time.Instant
import retrofit2.Response
import timber.log.Timber

class NextcloudNewsApiAdapter(
    private val api: NextcloudNewsApi
) : NewsApi {

    override suspend fun addFeed(uri: String): Feed {
        val response = api.postFeed(PostFeedArgs(uri, 0)).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        val feedJson = response.body()?.feeds?.single() ?: throw Exception("Can not parse server response")

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

    override suspend fun getNotViewedEntries(): Flow<GetNotViewedEntriesResult> = flow {
        emit(GetNotViewedEntriesResult.Loading(0L))

        val fetchedEntries = mutableSetOf<ItemJson>()
        val batchSize = 250L

        while (true) {
            val oldestEntryId = fetchedEntries.minOfOrNull { it.id ?: Long.MAX_VALUE }?.toLong() ?: 0L
            Timber.d("Oldest entry ID: $oldestEntryId")

            val response = try {
                api.getUnreadItems(
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
                val entries =  response.body()?.items ?: throw Exception("Can not parse server response")
                Timber.d("Got ${entries.size} entries")
                fetchedEntries += entries
                Timber.d("Fetched ${fetchedEntries.size} entries so far")
                emit(GetNotViewedEntriesResult.Loading(fetchedEntries.size.toLong()))

                if (entries.size < batchSize) {
                    break
                }
            }
        }

        Timber.d("Got ${fetchedEntries.size} entries in total")
        val validEntries = fetchedEntries.mapNotNull { it.toEntry() }
        Timber.d("Of them, valid: ${validEntries.size}")
        emit(GetNotViewedEntriesResult.Success(validEntries))
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        val response = api.getStarredItems().execute()

        if (!response.isSuccessful) {
            throw response.toException()
        } else {
            return response.body()?.items?.mapNotNull { it.toEntry() }
                ?: throw Exception("Can not parse server response")
        }
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        val response = api.getNewAndUpdatedItems(since.millis / 1000 + 1).execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }

        return response.body()?.items?.mapNotNull { it.toEntry() }
            ?: throw Exception("Can not parse server response")
    }

    override suspend fun markAsViewed(entriesIds: List<String>, viewed: Boolean) {
        val ids = entriesIds.map { it.toLong() }

        val response = if (viewed) {
            api.putRead(PutReadArgs(ids))
        } else {
            api.putUnread(PutReadArgs(ids))
        }.execute()

        if (!response.isSuccessful) {
            throw response.toException()
        }
    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
        val args = PutStarredArgs(entries.map { PutStarredArgsItem(it.feedId.toLong(), it.guidHash) })

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
            link = url ?: "",
            alternateLink = link ?: "",
            alternateLinkType = "text/html",
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
            published = published,
            updated = updated,
            authorName = author ?: "",
            content = body ?: "No content",
            enclosureLink = enclosureLink?.replace("http://", "https://") ?: "",
            enclosureLinkType = enclosureMime ?: "",

            viewed = unread == false,
            viewedSynced = true,

            bookmarked = starred,
            bookmarkedSynced = true,

            guidHash = guidHash ?: return null,
        )
    }

    private fun Response<*>.toException() = Exception("HTTPS request failed with error code ${code()}")
}