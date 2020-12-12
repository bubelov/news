package api

import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import org.joda.time.Instant

class NewsApiWrapper : NewsApi {

    lateinit var api: NewsApi

    override suspend fun addFeed(uri: String): Feed {
        return api.addFeed(uri)
    }

    override suspend fun getFeeds(): List<Feed> {
        return api.getFeeds()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {
        api.updateFeedTitle(feedId, newTitle)
    }

    override suspend fun deleteFeed(feedId: String) {
        api.deleteFeed(feedId)
    }

    override suspend fun getUnopenedEntries(): Flow<GetUnopenedEntriesResult> {
        return api.getUnopenedEntries()
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        return api.getBookmarkedEntries()
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        return api.getNewAndUpdatedEntries(since)
    }

    override suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean) {
        api.markAsOpened(entriesIds, opened)
    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
        api.markAsBookmarked(entries, bookmarked)
    }
}