package co.appreactor.news.api.standalone

import co.appreactor.news.api.GetUnopenedEntriesResult
import co.appreactor.news.api.NewsApi
import co.appreactor.news.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.Instant
import timber.log.Timber
import javax.xml.parsers.DocumentBuilderFactory

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
) : NewsApi {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(uri: String): Feed {
        val request = Request.Builder()
            .url(uri)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Response code: ${response.code}")
        }

        val responseBody = response.body ?: throw Exception("Response has empty body")
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(responseBody.byteStream())

        return when (document.getFeedType()) {
            FeedType.ATOM -> document.toAtomFeed()
            FeedType.RSS -> document.toRssFeed(uri)
            FeedType.UNKNOWN -> throw Exception("Unknown feed type")
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return feedQueries.selectAll().executeAsList()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getUnopenedEntries(): Flow<GetUnopenedEntriesResult> {
        return flowOf(GetUnopenedEntriesResult.Success(emptyList()))
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        return emptyList()
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        val entries = mutableListOf<Entry>()

        feedQueries.selectAll().executeAsList().forEach { feed ->
            val request = Request.Builder()
                .url(feed.selfLink)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@forEach
            }

            val responseBody = response.body ?: return@forEach
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = builder.parse(responseBody.byteStream())

            when (document.getFeedType()) {
                FeedType.ATOM -> entries += document.toAtomEntries()
                FeedType.RSS -> entries += document.toRssEntries()
                FeedType.UNKNOWN -> Timber.e(Exception("Unknown feed type for feed ${feed.id}"))
            }
        }

        entries.removeAll {
            entryQueries.selectById(it.id).executeAsOneOrNull() != null
        }

        return entries
    }

    override suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }
}