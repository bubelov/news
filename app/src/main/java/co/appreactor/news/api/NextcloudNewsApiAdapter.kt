package co.appreactor.news.api

import co.appreactor.news.db.Entry
import co.appreactor.news.db.EntryWithoutSummary
import co.appreactor.news.db.Feed
import kotlinx.datetime.*
import retrofit2.Response

class NextcloudNewsApiAdapter(
    private val api: NextcloudNewsApi
) : NewsApi {

    override suspend fun addFeed(url: String): Feed {
        val response = api.postFeed(PostFeedArgs(url, 0)).execute()

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

    override suspend fun getNotViewedEntries(): List<Entry> {
        val response = api.getUnreadItems().execute()

        if (!response.isSuccessful) {
            throw response.toException()
        } else {
            return response.body()?.items?.mapNotNull { it.toEntry() }
                ?: throw Exception("Can not parse server response")
        }
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
        val response = api.getNewAndUpdatedItems(since.epochSeconds + 1).execute()

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

        val published = Instant.fromEpochSeconds(pubDate).toLocalDateTime(TimeZone.UTC)
        val updated = Instant.fromEpochSeconds(lastModified).toLocalDateTime(TimeZone.UTC)

        return Entry(
            id = id.toString(),
            feedId = feedId?.toString() ?: "",
            title = title ?: "Untitled",
            link = url?.replace("http://", "https://") ?: "",
            published = published.toString(),
            updated = updated.toString(),
            authorName = author ?: "",
            summary = body ?: "No content",
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