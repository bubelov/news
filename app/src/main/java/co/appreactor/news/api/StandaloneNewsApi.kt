package co.appreactor.news.api

import co.appreactor.news.db.Entry
import co.appreactor.news.db.EntryWithoutSummary
import co.appreactor.news.db.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.joda.time.Instant

class StandaloneNewsApi : NewsApi {
    override suspend fun addFeed(url: String): Feed {
        TODO()
    }

    override suspend fun getFeeds(): List<Feed> {
        return emptyList()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getNotViewedEntries(): Flow<GetNotViewedEntriesResult> {
        return flowOf(GetNotViewedEntriesResult.Success(emptyList()))
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        return emptyList()
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        // TODO
        return emptyList()
    }

    override suspend fun markAsViewed(entriesIds: List<String>, viewed: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }
}