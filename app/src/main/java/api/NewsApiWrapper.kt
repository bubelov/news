package api

import db.Entry
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.flow.Flow
import org.joda.time.Instant
import java.net.URL

class NewsApiWrapper : NewsApi {

    lateinit var api: NewsApi

    override suspend fun addFeed(url: URL): Feed {
        return api.addFeed(url)
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

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> {
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        return api.getNewAndUpdatedEntries(since)
    }

    override suspend fun markEntriesAsOpened(entriesIds: List<String>, opened: Boolean) {
        api.markEntriesAsOpened(entriesIds, opened)
    }

    override suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {
        api.markEntriesAsBookmarked(entries, bookmarked)
    }
}