package api.standalone

import ParsedEntry
import ParsedFeed
import api.GetEntriesResult
import api.NewsApi
import common.trustSelfSignedCerts
import db.*
import getFeedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import logentries.LogEntriesRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.DateTime
import org.joda.time.Instant
import org.jsoup.Jsoup
import timber.log.Timber
import toAtomEntries
import toAtomFeed
import toRssEntries
import toRssFeed
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val log: LogEntriesRepository,
) : NewsApi {

    private val httpClient = OkHttpClient.Builder().trustSelfSignedCerts().build()

    override suspend fun addFeed(url: String): Feed {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Response code: ${response.code}")
        }

        val responseBody = response.body ?: throw Exception("Response has empty body")

        if (response.header("content-type")?.startsWith("text/html") == true) {
            val html = responseBody.string()

            val atomElements = Jsoup
                .parse(html)
                .select("link[type=\"application/rss+xml\"]")

            val rssElements = Jsoup
                .parse(html)
                .select("link[type=\"application/atom+xml\"]")

            if (atomElements.isEmpty() && rssElements.isEmpty()) {
                throw Exception("Cannot find feeds for $url")
            }

            return addFeed((atomElements + rssElements).first().attr("href"))
        } else {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = builder.parse(responseBody.byteStream())

            return when (document.getFeedType()) {
                FeedType.ATOM -> document.toAtomFeed(url).toFeed()
                FeedType.RSS -> document.toRssFeed(url).toFeed()
                FeedType.UNKNOWN -> throw Exception("Unknown feed type")
            }
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return feedQueries.selectAll().executeAsList()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getAllEntries(): Flow<GetEntriesResult> {
        return flowOf(GetEntriesResult.Success(emptyList()))
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        return emptyList()
    }

    override suspend fun getNewAndUpdatedEntries(
        since: Instant,
    ): List<Entry> = withContext(Dispatchers.IO) {
        log.insert(logEntry().copy(message = "getNewAndUpdatedEntries was called"))
        val startTimestamp = System.currentTimeMillis()
        val entries = mutableListOf<Entry>()

        feedQueries.selectAll().executeAsList().chunked(10).forEach { chunk ->
            chunk.map { feed ->
                async {
                    runCatching {
                        fetchEntries(feed.selfLink)
                    }.onSuccess {
                        synchronized(entries) { entries += it }
                    }.onFailure {
                        Timber.e(it, "Failed to fetch entries for feed ${feed.selfLink}")
                    }
                }
            }.awaitAll()
        }

        entries.removeAll {
            entryQueries.selectById(it.id).executeAsOneOrNull() != null
        }

        val totalTimeMillis = System.currentTimeMillis() - startTimestamp
        log.insert(logEntry().copy(message = "getNewAndUpdatedEntries was executed in $totalTimeMillis ms"))
        return@withContext entries
    }

    private fun fetchEntries(feedUrl: String): List<Entry> {
        val request = Request.Builder()
            .url(feedUrl)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception(response.toString())
        }

        val responseBody = response.body ?: throw Exception("Response has no body")
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(responseBody.byteStream())

        return when (document.getFeedType()) {
            FeedType.ATOM -> document.toAtomEntries().map { it.copy(feedId = feedUrl) }
                .map { it.toEntry() }
            FeedType.RSS -> document.toRssEntries().map { it.copy(feedId = feedUrl) }
                .map { it.toEntry() }
            FeedType.UNKNOWN -> throw Exception("Unknown feed type")
        }
    }

    override suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }

    private fun ParsedFeed.toFeed() = Feed(
        id = id,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
        openEntriesInBrowser = false,
        blockedWords = "",
    )

    private fun ParsedEntry.toEntry() = Entry(
        id = id,
        feedId = feedId,
        title = title,
        link = link,
        published = published,
        updated = updated,
        authorName = authorName,
        content = content,
        enclosureLink = enclosureLink,
        enclosureLinkType = enclosureLinkType,
        opened = false,
        openedSynced = true,
        bookmarked = false,
        bookmarkedSynced = false,
        guidHash = "",
    )

    private fun logEntry() = LogEntry(
        id = UUID.randomUUID().toString(),
        date = DateTime.now().toString(),
        tag = "standalone_news_api",
        message = "",
    )
}