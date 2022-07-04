package api.standalone

import android.util.Base64
import android.util.Log
import api.Api
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.AtomLink
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.feedk.FeedResult
import co.appreactor.feedk.RssFeed
import co.appreactor.feedk.RssItem
import co.appreactor.feedk.RssItemGuid
import co.appreactor.feedk.feed
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Db
import db.Entry
import db.EntryWithoutContent
import db.Feed
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val db: Db,
) : Api {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
        val request = Request.Builder().url(url).build()

        runCatching {
            httpClient.newCall(request).execute()
        }.getOrElse {
            return Result.failure(it)
        }.use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("Cannot fetch feed (url = $url, code = ${response.code})"))
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

    override suspend fun getFeeds(): Result<List<Feed>> {
        return runCatching { db.feedQueries.selectAll().asFlow().mapToList().first() }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> {
        return flowOf(emptyList())
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        val entries = mutableListOf<Entry>()

        withContext(Dispatchers.Default) {
            db.feedQueries.selectAll().executeAsList().forEach { feed ->
                runCatching {
                    entries += fetchEntries(feed)
                }.onFailure {
                    Log.e("api", "Failed to fetch entries for feed: $feed", it)
                }
            }
        }

        db.transaction {
            entries.removeAll { db.entryQueries.selectById(it.id).executeAsOneOrNull() != null }
        }

        return entries
    }

    private fun fetchEntries(feed: Feed): List<Entry> {
        val url = feed.links.first { it.rel == "self" }.href
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
                            parsedFeed.entries
                                .getOrElse { return emptyList() }
                                .mapNotNull { atomEntry ->
                                    runCatching {
                                        atomEntry.toEntry(feed.id)
                                    }.onFailure {
                                        Log.e("api", "Failed to parse Atom entry: $atomEntry", it)
                                    }.getOrNull()
                                }
                        }
                        is RssFeed -> {
                            parsedFeed.channel.items
                                .getOrElse { return emptyList() }
                                .mapNotNull { it.getOrNull() }
                                .mapNotNull { rssItem ->
                                    runCatching {
                                        rssItem.toEntry(feed.id)
                                    }.onFailure {
                                        Log.e("api", "Failed to parse RSS item: $rssItem", it)
                                    }.getOrNull()
                                }
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
                val links = links.map { it.toLink(feedId = selfLink.href, entryId = null) }

                val feed = Feed(
                    id = selfLink.href,
                    title = title,
                    links = links,
                    openEntriesInBrowser = false,
                    blockedWords = "",
                    showPreviewImages = null,
                )

                feed
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

                Feed(
                    id = channel.link,
                    title = channel.title,
                    links = listOf(selfLink, alternateLink),
                    openEntriesInBrowser = false,
                    blockedWords = "",
                    showPreviewImages = null,
                )
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

    private fun AtomEntry.toEntry(feedId: String): Entry {
        val links = links.map { it.toLink(feedId = null, entryId = id) }

        return Entry(
            contentType = content.type.toString(),
            contentSrc = content.src,
            contentText = content.text,
            links = links,
            summary = "",
            id = id,
            feedId = feedId,
            title = title,
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = authorName,
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

        // https://practicaltypography.com/line-length.html
        // 45–90 characters per line x 3 lines
        val maxSummaryLength = 180

        return Entry(
            contentType = "html",
            contentSrc = "",
            contentText = description ?: "",
            links = links,
            summary = if ((description?.length ?: 0) < maxSummaryLength) description else "",
            id = id,
            feedId = feedId,
            title = title ?: "",
            published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
            authorName = author ?: "",
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