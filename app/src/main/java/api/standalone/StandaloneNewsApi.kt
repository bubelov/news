package api.standalone

import android.util.Base64
import api.NewsApi
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.RssFeed
import co.appreactor.feedk.RssItem
import co.appreactor.feedk.feed
import common.trustSelfSignedCerts
import db.Entry
import db.EntryQueries
import db.EntryWithoutSummary
import db.Feed
import db.FeedQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

typealias ParsedFeed = co.appreactor.feedk.Feed

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
) : NewsApi {

    private val http = OkHttpClient.Builder().trustSelfSignedCerts().build()

    override suspend fun addFeed(url: URL): Feed {
        Timber.d("Trying to add feed with URL $url")
        val request = Request.Builder()
            .url(url)
            .build()

        val response = http.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Response code: ${response.code}")
        }

        val responseBody = response.body ?: throw Exception("Response has empty body")

        if (response.header("content-type")?.startsWith("text/html") == true) {
            Timber.d("Got an HTML document, looking for feed links")
            val html = Jsoup.parse(responseBody.string())

            val feedElements = mutableListOf<Element>().apply {
                addAll(html.select("link[type=\"application/rss+xml\"]"))
                addAll(html.select("link[type=\"application/atom+xml\"]"))
            }

            if (feedElements.isEmpty()) {
                throw Exception("Cannot find feeds for $url")
            }

            Timber.d("Feed elements found: ${feedElements.size}. Data: $feedElements")
            return addFeed(URI.create(feedElements.first().attr("href")).toURL())
        } else {
            return feed(url).getOrThrow().toFeed(url)
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return feedQueries.selectAll().executeAsList()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> {
        return flowOf(emptyList())
    }

    // TODO return updated entries
    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> = withContext(Dispatchers.IO) {
        Timber.d("Fetching new and updated entries")
        val startTimestamp = System.currentTimeMillis()
        val entries = mutableListOf<Entry>()

        feedQueries.selectAll().executeAsList().chunked(10).forEach { chunk ->
            chunk.map { feed -> async { entries.addAll(fetchEntries(feed)) } }.awaitAll()
        }

        entries.removeAll {
            entryQueries.selectById(it.id).executeAsOneOrNull() != null
        }

        val totalTimeMillis = System.currentTimeMillis() - startTimestamp
        Timber.d("Fetched new and updated entries in $totalTimeMillis ms")
        return@withContext entries
    }

    private fun fetchEntries(feed: Feed): List<Entry> {
        val url = runCatching {
            URI.create(feed.selfLink).toURL()
        }.getOrElse {
            Timber.d("Failed to parse feed url for feed $feed")
            return emptyList()
        }

        val parsedFeed = feed(url).getOrElse {
            Timber.d("Failed to parse feed $feed")
            return emptyList()
        }

        return when (parsedFeed) {
            is AtomFeed -> {
                parsedFeed.entries.getOrElse {
                    Timber.d("Failed to parse Atom entries for feed $feed")
                    return emptyList()
                }.map { it.toEntry(feed.id) }
            }
            is RssFeed -> {
                parsedFeed.channel.items.getOrElse {
                    Timber.d("Failed to parse RSS entries for feed $feed")
                    return emptyList()
                }.mapNotNull {
                    it.getOrElse {
                        Timber.d("Failed to parse RSS entry for feed $feed")
                        null
                    }
                }.map { it.toEntry(feed.id) }
            }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {

    }

    override suspend fun markEntriesAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }

    private fun ParsedFeed.toFeed(feedUrl: URL): Feed {
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
                selfLink = feedUrl.toString(),
                alternateLink = channel.link.toString(),
                openEntriesInBrowser = false,
                blockedWords = "",
                showPreviewImages = null,
            )
        }
    }

    private fun AtomEntry.toEntry(feedId: String) = Entry(
        id = id,
        feedId = feedId,
        title = title,
        link = link,
        published = OffsetDateTime.parse(published),
        updated = OffsetDateTime.parse(updated),
        authorName = authorName,
        content = content,
        enclosureLink = enclosureLink,
        enclosureLinkType = enclosureLinkType,
        read = false,
        readSynced = true,
        bookmarked = false,
        bookmarkedSynced = true,
        guidHash = "",
        commentsUrl = "",
    )

    private fun RssItem.toEntry(feedId: String) = Entry(
        id = sha256("$feedId:$title:$description"),
        feedId = feedId,
        title = title ?: "",
        link = link?.toString() ?: "",
        published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
        updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
        authorName = author ?: "",
        content = description ?: "",
        enclosureLink = enclosure?.url.toString(),
        enclosureLinkType = enclosure?.type ?: "",
        read = false,
        readSynced = true,
        bookmarked = false,
        bookmarkedSynced = true,
        guidHash = "",
        commentsUrl = "",
    )

    private fun sha256(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(string.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun Date.toIsoString(): String = ISO.format(this)

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}