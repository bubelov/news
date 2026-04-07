package org.vestifeed.api.standalone

import android.util.Base64
import org.vestifeed.api.Api
import org.vestifeed.parser.AtomEntry
import org.vestifeed.parser.AtomFeed
import org.vestifeed.parser.AtomLink
import org.vestifeed.parser.AtomLinkRel
import org.vestifeed.parser.FeedResult
import org.vestifeed.parser.RssFeed
import org.vestifeed.parser.RssItem
import org.vestifeed.parser.RssItemGuid
import org.vestifeed.parser.feed
import org.vestifeed.db.Database
import org.vestifeed.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.Link
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

typealias ParsedFeed = org.vestifeed.parser.Feed

class StandaloneNewsApi(
    private val db: Database,
) : Api {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(url: HttpUrl): Api.AddFeedResult {
        val request = Request.Builder().url(url).build()

        runCatching {
            httpClient.newCall(request).await()
        }.getOrElse {
            throw it
        }.use { response ->
            if (!response.isSuccessful) {
                throw Exception("Cannot fetch feed (url = $url, code = ${response.code})")
            }

            val contentType = response.header("content-type") ?: ""

            if (contentType.startsWith("text/html")) {
                val html = runCatching {
                    withContext(Dispatchers.IO) {
                        Jsoup.parse(response.body!!.string())
                    }
                }.getOrElse {
                    throw Exception("Failed to read response", it)
                }

                val feedElements = buildList {
                    addAll(html.select("link[type=\"application/atom+xml\"]"))
                    addAll(html.select("link[type=\"application/rss+xml\"]"))
                }

                if (feedElements.isEmpty()) {
                    throw Exception("Cannot find feed links in HTML page (url = $url)")
                }

                val href = feedElements.first().attr("href")
                val absolute = !href.startsWith("/")

                return if (absolute) {
                    addFeed(href.toHttpUrl())
                } else {
                    addFeed("$url$href".toHttpUrl())
                }
            } else {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        feed(response.body.byteStream(), contentType)
                    }
                }.getOrElse {
                    throw Exception("Failed to read response", it)
                }

                return when (result) {
                    is FeedResult.Success -> {
                        val (feed, feedLinks) = result.feed.toFeed(url)
                        Api.AddFeedResult(
                            feed = feed,
                            feedLinks = feedLinks,
                            entries = result.feed.getEntries(feed.id)
                        )
                    }

                    is FeedResult.UnsupportedMediaType -> {
                        throw Exception("Unsupported media type: ${result.mediaType}")
                    }

                    is FeedResult.UnsupportedFeedType -> {
                        throw Exception("Unsupported feed type")
                    }

                    is FeedResult.IOError -> {
                        throw result.cause
                    }

                    is FeedResult.ParserError -> {
                        throw result.cause
                    }
                }
            }
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return db.feed.selectAll()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>> {
        return flowOf(emptyList())
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>> {
        val fetchedEntries = mutableListOf<Pair<Entry, List<Link>>>()
        val feeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
        feeds.forEach { fetchedEntries += fetchEntries(it) }
        return fetchedEntries
    }

    private suspend fun fetchEntries(feed: Feed): List<Pair<Entry, List<Link>>> {
        val feedLinks = withContext(Dispatchers.IO) { db.link.selectByFeedId(feed.id) }
        val feedSelfLink = feedLinks.firstOrNull { it.rel is AtomLinkRel.Self }
            ?: throw Exception("self link is missing")
        val request = Request.Builder().url(feedSelfLink.href).build()
        val response = httpClient.newCall(request).await()
        response.use {
            if (!response.isSuccessful) throw Exception("feed request failed")
            val feedResult = feed(response.body.byteStream(), response.header("content-type") ?: "")
            return when (feedResult) {
                is FeedResult.Success -> {
                    when (val parsedFeed = feedResult.feed) {
                        is AtomFeed -> {
                            parsedFeed.entries.map { atomEntry -> atomEntry.toEntry(feed.id) }
                        }

                        is RssFeed -> {
                            parsedFeed.channel.items
                                .getOrElse { return emptyList() }
                                .mapNotNull { it.getOrNull() }
                                .map { rssItem -> rssItem.toEntry(feed.id) }
                        }
                    }
                }

                is FeedResult.UnsupportedMediaType -> {
                    throw Exception("unsupported media type")
                }

                is FeedResult.UnsupportedFeedType -> {
                    throw Exception("unsupported feed type")
                }

                is FeedResult.IOError -> {
                    throw feedResult.cause
                }

                is FeedResult.ParserError -> {
                    throw feedResult.cause
                }
            }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {

    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryQueries.EntryWithoutContent>,
        bookmarked: Boolean
    ) {

    }

    private fun ParsedFeed.toFeed(feedUrl: HttpUrl): Pair<Feed, List<Link>> {
        return when (this) {
            is AtomFeed -> {
                val selfLink = links.single { it.rel == AtomLinkRel.Self }
                val links = links.map { it.toLink(feedId = selfLink.href, entryId = null) }

                Pair(
                    Feed(
                        id = selfLink.href,
                        title = title,
                        extOpenEntriesInBrowser = false,
                        extBlockedWords = "",
                        extShowPreviewImages = null,
                    ), links
                )
            }

            is RssFeed -> {
                val selfLink = Link(
                    feedId = channel.link,
                    entryId = null,
                    href = feedUrl.toString(),
                    rel = AtomLinkRel.Self,
                    type = null,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                    id = null,
                )

                val alternateLink = Link(
                    feedId = channel.link,
                    entryId = null,
                    href = channel.link,
                    rel = AtomLinkRel.Alternate,
                    type = null,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                    id = null,
                )

                Pair(
                    Feed(
                        id = channel.link,
                        title = channel.title,
                        extOpenEntriesInBrowser = false,
                        extBlockedWords = "",
                        extShowPreviewImages = null,
                    ), listOf(selfLink, alternateLink)
                )
            }
        }
    }

    private fun AtomLink.toLink(
        feedId: String?,
        entryId: String?,
    ): Link {
        return Link(
            id = null,
            feedId = feedId,
            entryId = entryId,
            href = href,
            rel = rel,
            type = type,
            hreflang = hreflang,
            title = title,
            length = length,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
    }

    private fun AtomEntry.toEntry(feedId: String): Pair<Entry, List<Link>> {
        return Pair(
            Entry(
                contentType = content.type.toString(),
                contentSrc = content.src,
                contentText = content.text,
                summary = summary?.text ?: "",
                id = id,
                feedId = feedId,
                title = title,
                published = OffsetDateTime.parse(published),
                updated = OffsetDateTime.parse(updated),
                authorName = authorName,
                extRead = false,
                extReadSynced = true,
                extBookmarked = false,
                extBookmarkedSynced = true,
                extCommentsUrl = "",
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), links.map {
                Link(
                    id = null,
                    feedId = null,
                    entryId = id,
                    href = it.href,
                    rel = it.rel,
                    type = it.type,
                    hreflang = it.hreflang,
                    title = it.title,
                    length = it.length,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )
            }
        )
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
                id = null,
                feedId = null,
                entryId = id,
                href = link,
                rel = AtomLinkRel.Alternate,
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
                id = null,
                feedId = null,
                entryId = id,
                href = enclosure.url.toString(),
                rel = AtomLinkRel.Enclosure,
                type = enclosure.type,
                hreflang = "",
                title = "",
                length = enclosure.length,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Pair(
            Entry(
                contentType = "html",
                contentSrc = "",
                contentText = description ?: "",
                summary = description,
                id = id,
                feedId = feedId,
                title = title ?: "",
                published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
                updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
                authorName = author ?: "",
                extRead = false,
                extReadSynced = true,
                extBookmarked = false,
                extBookmarkedSynced = true,
                extCommentsUrl = "",
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), emptyList()
        )
    }

    private fun sha256(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(string.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun Date.toIsoString(): String = ISO.format(this)

    private fun ParsedFeed.getEntries(feedId: String): List<Pair<Entry, List<Link>>> {
        return when (this) {
            is RssFeed -> {
                this.channel.items
                    .getOrElse { emptyList() }
                    .filter { it.isSuccess }
                    .map { it.getOrThrow().toEntry(feedId) }
            }

            is AtomFeed -> {
                this.entries.map { it.toEntry(feedId) }
            }
        }
    }

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}