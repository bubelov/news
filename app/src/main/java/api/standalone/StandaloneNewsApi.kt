package api.standalone

import ParsedEntry
import ParsedFeed
import api.GetEntriesResult
import api.NewsApi
import db.*
import getFeedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.Instant
import org.jsoup.Jsoup
import timber.log.Timber
import toAtomEntries
import toAtomFeed
import toRssEntries
import toRssFeed
import javax.xml.parsers.DocumentBuilderFactory

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
) : NewsApi {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(uri: String): Feed {
        val isHttps = uri.startsWith("https")
        val isCleartextHttp = !isHttps && uri.startsWith("http")

        if (isCleartextHttp) {
            throw Exception("Insecure feeds are not allowed. Please use HTTPS.")
        }

        val fullUri = if (!isHttps) {
            "https://$uri"
        } else {
            uri
        }

        return getFeed(fullUri)
    }

    private fun getFeed(uri: String): Feed {
        val request = Request.Builder()
            .url(uri)
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
                throw Exception("Cannot find feeds for $uri")
            }

            return getFeed((atomElements + rssElements).first().attr("href"))
        } else {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = builder.parse(responseBody.byteStream())

            return when (document.getFeedType()) {
                FeedType.ATOM -> document.toAtomFeed(uri).toFeed()
                FeedType.RSS -> document.toRssFeed(uri).toFeed()
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
        val entries = mutableListOf<Entry>()

        feedQueries.selectAll().executeAsList().forEach { feed ->
            runCatching {
                entries += fetchEntries(feed.selfLink)
            }.onFailure {
                Timber.e("Failed to fetch entries for feed ${feed.selfLink}")
            }
        }

        entries.removeAll {
            entryQueries.selectById(it.id).executeAsOneOrNull() != null
        }

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
            FeedType.ATOM -> document.toAtomEntries().map { it.toEntry() }
            FeedType.RSS -> document.toRssEntries().map { it.toEntry() }
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
}