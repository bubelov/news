package api.standalone

import ParsedEntry
import ParsedFeed
import api.GetEntriesResult
import api.NewsApi
import db.*
import getFeedType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

        if (response.header("content-type") == "text/html") {
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
                FeedType.ATOM -> entries += document.toAtomEntries().map { it.toEntry() }
                FeedType.RSS -> entries += document.toRssEntries().map { it.toEntry() }
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