package api.standalone

import android.util.Base64
import api.NewsApi
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.FeedResult
import co.appreactor.feedk.RssFeed
import co.appreactor.feedk.RssItem
import co.appreactor.feedk.RssItemGuid
import co.appreactor.feedk.feed
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import timber.log.Timber
import java.net.HttpURLConnection
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

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
        Timber.d("Adding feed (url = $url)")

        val oldUrl = url.toUrl()

        val connection = runCatching {
            oldUrl.openConnection() as HttpURLConnection
        }.getOrElse {
            return Result.failure(it)
        }

        runCatching {
            connection.connect()
        }.getOrElse {
            return Result.failure(it)
        }

        val httpResponseCode = connection.responseCode

        if (httpResponseCode != 200) {
            val e = Exception("Cannot fetch feed (url = $url, code = $httpResponseCode)")
            return Result.failure(e)
        }

        if (connection.getHeaderField("content-type")?.startsWith("text/html") == true) {
            Timber.d("Got an HTML document, looking for feed links")
            val html = Jsoup.parse(connection.inputStream.bufferedReader().readText())

            val feedElements = buildList {
                addAll(html.select("link[type=\"application/rss+xml\"]"))
                addAll(html.select("link[type=\"application/atom+xml\"]"))
            }

            if (feedElements.isEmpty()) {
                throw Exception("Cannot find feeds for $url")
            }

            Timber.d("Feed elements found: ${feedElements.size}. Data: $feedElements")
            val href = feedElements.first().attr("href")
            val absolute = !href.startsWith("/")

            return if (absolute) {
                addFeed(href.toHttpUrl())
            } else {
                addFeed("$oldUrl$href".toHttpUrl())
            }
        } else {
            return when (val result = feed(oldUrl, connection)) {
                is FeedResult.Success -> {
                    Result.success(result.feed.toFeed(oldUrl))
                }

                is FeedResult.HttpConnectionFailure -> {
                    Result.failure(Exception("HTTP connection failure"))
                }

                is FeedResult.HttpNotOk -> {
                    Result.failure(Exception("Got HTTP response code ${result.responseCode} with message: ${result.message}"))
                }

                is FeedResult.ParserFailure -> {
                    Result.failure(result.t)
                }

                FeedResult.UnknownFeedType -> {
                    Result.failure(Exception("Unknown feed type"))
                }
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
            Timber.e(it, "Failed to parse feed url for feed $feed")
            return emptyList()
        }

        val feedResult = runCatching {
            feed(url)
        }.getOrElse {
            Timber.e(it, "Failed to fetch feed $feed")
            return emptyList()
        }

        return when (feedResult) {
            is FeedResult.Success -> {
                when (val parsedFeed = feedResult.feed) {
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

            is FeedResult.HttpConnectionFailure -> {
                Timber.e("HTTP connection failure (url = $url)")
                emptyList()
            }

            is FeedResult.HttpNotOk -> {
                Timber.e("Got HTTP response code ${feedResult.responseCode} with message: ${feedResult.message}")
                emptyList()
            }

            is FeedResult.ParserFailure -> {
                Timber.e(feedResult.t, "Feed parser failure (url = $url)")
                emptyList()
            }

            FeedResult.UnknownFeedType -> {
                Timber.e("Unknown feed type (url = $url)")
                emptyList()
            }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {

    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutSummary>,
        bookmarked: Boolean
    ) {

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

    private fun RssItem.toEntry(feedId: String): Entry {
        val id = when (val guid = guid) {
            is RssItemGuid.StringGuid -> "guid:${guid.value}"
            is RssItemGuid.UrlGuid -> "guid:${guid.value}"
            else -> {
                val feedIdComponent = "feed-id:$feedId"
                val titleHashComponent = "title-sha256:${sha256(title ?: "")}"
                val descriptionHashComponent = "description-sha256:${sha256(description ?: "")}"
                "$feedIdComponent,$titleHashComponent,$descriptionHashComponent"
            }
        }

        return Entry(
            id = id,
            feedId = feedId,
            title = title ?: "",
            link = link?.toString() ?: "",
            published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            authorName = author ?: "",
            content = description ?: "",
            enclosureLink = enclosure?.url?.toString() ?: "",
            enclosureLinkType = enclosure?.type ?: "",
            read = false,
            readSynced = true,
            bookmarked = false,
            bookmarkedSynced = true,
            guidHash = "",
            commentsUrl = "",
        )
    }

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