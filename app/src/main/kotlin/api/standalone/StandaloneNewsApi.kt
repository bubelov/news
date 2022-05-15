package api.standalone

import android.util.Base64
import api.NewsApi
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.AtomLink
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
import db.Link
import db.LinkQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

typealias ParsedFeed = co.appreactor.feedk.Feed

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val linkQueries: LinkQueries,
) : NewsApi {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Link>>> {
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
                    addAll(html.select("link[type=\"application/atom+xml\"]"))
                    addAll(html.select("link[type=\"application/rss+xml\"]"))
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

    override suspend fun getFeeds(): List<Pair<Feed, List<Link>>> {
        val result: MutableList<Pair<Feed, List<Link>>> = mutableListOf()

        feedQueries.transaction {
            feedQueries.selectAll().executeAsList().onEach {
                result += Pair(it, linkQueries.selectByFeedId(it.id).executeAsList())
            }
        }

        return result
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>> {
        return flowOf(emptyList())
    }

    // TODO return updated entries
    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<Pair<Entry, List<Link>>>()

        feedQueries.selectAll().executeAsList().chunked(10).forEach { chunk ->
            chunk.map { feed ->
                async {
                    entries.addAll(fetchEntries(Pair(feed, linkQueries.selectByFeedId(feed.id).executeAsList())))
                }
            }.awaitAll()
        }

        entries.removeAll {
            entryQueries.selectById(it.first.id).executeAsOneOrNull() != null
        }

        return@withContext entries
    }

    private fun fetchEntries(feed: Pair<Feed, List<Link>>): List<Pair<Entry, List<Link>>> {
        val url = feed.second.first { it.rel == "self" }.href
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
                            }.map { it.toEntry(feed.first.id) }
                        }
                        is RssFeed -> {
                            parsedFeed.channel.items.getOrElse {
                                return emptyList()
                            }.mapNotNull { it.getOrNull() }.map { it.toEntry(feed.first.id) }
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

    private fun ParsedFeed.toFeed(feedUrl: HttpUrl): Pair<Feed, List<Link>> {
        return when (this) {
            is AtomFeed -> {
                val selfLink = links.single { it.rel == AtomLinkRel.Self }
                val links = links.map { it.toLink(feedId = selfLink.href, entryId = null) }

                val feed = Feed(
                    id = selfLink.href,
                    title = title,
                    openEntriesInBrowser = false,
                    blockedWords = "",
                    showPreviewImages = null,
                )

                Pair(feed, links)
            }
            is RssFeed -> {
                val selfLink = Link(
                    feedId = channel.link,
                    entryId = null,
                    href = feedUrl,
                    rel = "self",
                    type = null,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )

                val alternateLink = Link(
                    feedId = channel.link,
                    entryId = null,
                    href = channel.link.toHttpUrl(),
                    rel = "alternate",
                    type = null,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )

                val feed = Feed(
                    id = channel.link,
                    title = channel.title,
                    openEntriesInBrowser = false,
                    blockedWords = "",
                    showPreviewImages = null,
                )

                Pair(feed, listOf(selfLink, alternateLink))
            }
        }
    }

    private fun AtomLink.toLink(
        feedId: String?,
        entryId: String?,
    ): Link {
        return Link(
            feedId = feedId,
            entryId = entryId,
            href = href.toHttpUrl(),
            rel = rel?.asSting() ?: "",
            type = type,
            hreflang = hreflang,
            title = title,
            length = length,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
    }

    private fun AtomEntry.toEntry(feedId: String): Pair<Entry, List<Link>> {
        val links = links.map { it.toLink(feedId = null, entryId = id) }

        val entry = Entry(
            id = id,
            feedId = feedId,
            title = title,
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = authorName,
            contentType = content.type.toString(),
            contentSrc = content.src,
            contentText = content.text,
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

        return Pair(entry, links)
    }

    private fun RssItem.toEntry(feedId: String): Pair<Entry, List<Link>> {
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

        val links = mutableListOf<Link>()

        if (!link.isNullOrBlank()) {
            links += Link(
                feedId = null,
                entryId = id,
                href = link!!.toHttpUrl(),
                rel = "alternate",
                type = "text/html",
                hreflang = "",
                title = "",
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        if (enclosure != null) {
            links += Link(
                feedId = null,
                entryId = id,
                href = enclosure!!.url.toHttpUrlOrNull()!!,
                rel = "enclosure",
                type = enclosure!!.type,
                hreflang = "",
                title = "",
                length = enclosure!!.length,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        val entry = Entry(
            id = id,
            feedId = feedId,
            title = title ?: "",
            published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            authorName = author ?: "",
            contentType = "html",
            contentSrc = "",
            contentText = description ?: "",
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

        return Pair(entry, links)
    }

    private fun sha256(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(string.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun Date.toIsoString(): String = ISO.format(this)

    private fun AtomLinkRel.asSting(): String {
        return when (this) {
            AtomLinkRel.Alternate -> "alternate"
            AtomLinkRel.Enclosure -> "enclosure"
            AtomLinkRel.Related -> "related"
            AtomLinkRel.Self -> "self"
            AtomLinkRel.Via -> "via"
        }
    }

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}