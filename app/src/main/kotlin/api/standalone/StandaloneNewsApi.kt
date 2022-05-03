package api.standalone

import android.util.Base64
import api.NewsApi
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.feedk.FeedResult
import co.appreactor.feedk.RssFeed
import co.appreactor.feedk.RssItem
import co.appreactor.feedk.RssItemGuid
import co.appreactor.feedk.feed
import db.Entry
import db.EntryQueries
import db.EntryWithoutContent
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
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

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
        val request = Request.Builder().url(url).build()

        runCatching {
            httpClient.newCall(request).execute()
        }.getOrElse {
            return Result.failure(it)
        }.use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("Cannot fetch feed (url = $url, code = $response)"))
            }

            val contentType = response.header("content-type") ?: ""

            if (contentType.startsWith("text/html")) {
                val html = Jsoup.parse(response.body!!.string())

                val feedElements = buildList {
                    addAll(html.select("link[type=\"application/rss+xml\"]"))
                    addAll(html.select("link[type=\"application/atom+xml\"]"))
                }

                if (feedElements.isEmpty()) {
                    return Result.failure(Exception("Cannot find feed links in HTML page (url = $url)"))
                }

                val href = feedElements.first().attr("href")
                val absolute = !href.startsWith("/")

                return if (absolute) {
                    addFeed(href.toHttpUrl())
                } else {
                    addFeed("$url$href".toHttpUrl())
                }
            } else {
                return when (val result = feed(response.body!!.byteStream(), contentType)) {
                    is FeedResult.Success -> {
                        Result.success(result.feed.toFeed(url))
                    }

                    is FeedResult.UnsupportedMediaType -> {
                        Result.failure(Exception("Unsupported media type: ${result.mediaType}"))
                    }

                    is FeedResult.UnsupportedFeedType -> {
                        Result.failure(Exception("Unsupported feed type"))
                    }

                    is FeedResult.IOError -> {
                        Result.failure(result.cause)
                    }

                    is FeedResult.ParserError -> {
                        Result.failure(result.cause)
                    }
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
        val url = feed.selfLink.toHttpUrl()

        val request = Request.Builder().url(url).build()

        val response = runCatching {
            httpClient.newCall(request).execute()
        }.getOrElse {
            return emptyList()
        }

        response.use {
            if (!response.isSuccessful) return emptyList()

            val feedResult = runCatching {
                feed(response.body!!.byteStream(), response.header("content-type") ?: "")
            }.getOrElse {
                return emptyList()
            }

            return when (feedResult) {
                is FeedResult.Success -> {
                    when (val parsedFeed = feedResult.feed) {
                        is AtomFeed -> {
                            parsedFeed.entries.getOrElse {
                                return emptyList()
                            }.map { it.toEntry(feed.id) }
                        }
                        is RssFeed -> {
                            parsedFeed.channel.items.getOrElse {
                                return emptyList()
                            }.mapNotNull { it.getOrNull() }.map { it.toEntry(feed.id) }
                        }
                    }
                }

                is FeedResult.UnsupportedMediaType -> {
                    emptyList()
                }

                is FeedResult.UnsupportedFeedType -> {
                    emptyList()
                }

                is FeedResult.IOError -> {
                    emptyList()
                }

                is FeedResult.ParserError -> {
                    emptyList()
                }
            }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {

    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean
    ) {

    }

    private fun ParsedFeed.toFeed(feedUrl: HttpUrl): Feed {
        return when (this) {
            is AtomFeed -> {
                val selfLink = links.single { it.rel == AtomLinkRel.Self }
                val alternateLink = links.single { it.rel == AtomLinkRel.Alternate }

                Feed(
                    id = selfLink.href,
                    title = title,
                    selfLink = selfLink.href,
                    alternateLink = alternateLink.href,
                    openEntriesInBrowser = false,
                    blockedWords = "",
                    showPreviewImages = null,
                )
            }
            is RssFeed -> Feed(
                id = channel.link,
                title = channel.title,
                selfLink = feedUrl.toString(),
                alternateLink = channel.link,
                openEntriesInBrowser = false,
                blockedWords = "",
                showPreviewImages = null,
            )
        }
    }

    private fun AtomEntry.toEntry(feedId: String): Entry {
        val alternateLink = links.firstOrNull { it.rel == AtomLinkRel.Alternate }
        val enclosureLink = links.firstOrNull { it.rel == AtomLinkRel.Enclosure }

        return Entry(
            id = id,
            feedId = feedId,
            title = title,
            link = alternateLink?.href ?: "",
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = authorName,
            contentType = content.type.toString(),
            contentSrc = content.src,
            contentText = content.text,
            enclosureLink = enclosureLink?.href ?: "",
            enclosureLinkType = enclosureLink?.type ?: "",
            read = false,
            readSynced = true,
            bookmarked = false,
            bookmarkedSynced = true,
            guidHash = "",
            commentsUrl = "",
            ogImageChecked = false,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
        )
    }

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
            link = link ?: "",
            published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            authorName = author ?: "",
            contentType = "html",
            contentSrc = "",
            contentText = description ?: "",
            enclosureLink = enclosure?.url?.toString() ?: "",
            enclosureLinkType = enclosure?.type ?: "",
            read = false,
            readSynced = true,
            bookmarked = false,
            bookmarkedSynced = true,
            guidHash = "",
            commentsUrl = "",
            ogImageChecked = false,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
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