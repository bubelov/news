package api.standalone

import api.GetEntriesResult
import api.NewsApi
import co.appreactor.feedk.*
import common.trustSelfSignedCerts
import db.*
import db.Feed
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
import java.net.URI
import java.util.*

typealias ParsedFeed = co.appreactor.feedk.Feed

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
            return feed(URI.create(url).toURL()).getOrThrow().toFeed()
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
                        fetchEntries(feed)
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

    private fun fetchEntries(feed: Feed): List<Entry> {
        return when (val freshFeed = feed(URI.create(feed.selfLink).toURL()).getOrThrow()) {
            is AtomFeed -> freshFeed.entries.getOrThrow().map { it.toEntry() }
            is RssFeed -> freshFeed.channel.items.getOrThrow().map { it.getOrThrow().toEntry() }
        }
    }

    override suspend fun markAsOpened(entriesIds: List<String>, opened: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }

    private fun ParsedFeed.toFeed(): Feed {
        return when (this) {
            is AtomFeed -> Feed(
                id = selfLink,
                title = title,
                selfLink = selfLink,
                alternateLink = alternateLink,
                openEntriesInBrowser = false,
                blockedWords = "",
                showPreviewImages = null,
            )
            is RssFeed -> Feed(
                id = channel.link.toString(),
                title = channel.title,
                selfLink = "",
                alternateLink = channel.link.toString(),
                openEntriesInBrowser = false,
                blockedWords = "",
                showPreviewImages = null,
            )
        }
    }

    private fun AtomEntry.toEntry() = Entry(
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

    private fun RssItem.toEntry() = Entry(
        id = "",
        feedId = "",
        title = title ?: "",
        link = link.toString(),
        published = pubDate.toString(),
        updated = "",
        authorName = "",
        content = description ?: "",
        enclosureLink = enclosure?.url.toString(),
        enclosureLinkType = enclosure?.type ?: "",
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