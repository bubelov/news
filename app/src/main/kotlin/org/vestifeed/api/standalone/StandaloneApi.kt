package org.vestifeed.api.standalone

import android.util.Base64
import android.util.Log
import org.vestifeed.api.Api
import co.appreactor.feedk.AtomEntry
import co.appreactor.feedk.AtomFeed
import co.appreactor.feedk.AtomLink
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.feedk.FeedResult
import co.appreactor.feedk.RssFeed
import co.appreactor.feedk.RssItem
import co.appreactor.feedk.RssItemGuid
import co.appreactor.feedk.feed
import org.vestifeed.db.Db
import org.vestifeed.db.Entry
import org.vestifeed.db.EntryWithoutContent
import org.vestifeed.db.Feed
import org.vestifeed.db.Link
import org.vestifeed.http.await
import kotlinx.coroutines.Dispatchers
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
import java.util.Collections
import java.util.Date
import java.util.Locale

typealias ParsedFeed = co.appreactor.feedk.Feed

class StandaloneNewsApi(
    private val db: Db,
) : Api {

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
        val request = Request.Builder().url(url).build()

        runCatching {
            httpClient.newCall(request).await()
        }.getOrElse {
            return Result.failure(it)
        }.use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("Cannot fetch feed (url = $url, code = ${response.code})"))
            }

            val contentType = response.header("content-type") ?: ""

            if (contentType.startsWith("text/html")) {
                val html = runCatching {
                    withContext(Dispatchers.IO) {
                        Jsoup.parse(response.body!!.string())
                    }
                }.getOrElse {
                    return Result.failure(Exception("Failed to read response", it))
                }

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
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        feed(response.body!!.byteStream(), contentType)
                    }
                }.getOrElse {
                    return Result.failure(Exception("Failed to read response", it))
                }

                return when (result) {
                    is FeedResult.Success -> {
                        val feed = result.feed.toFeed(url)
                        Result.success(Pair(feed, result.feed.getEntries(feed.id)))
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
        return runCatching { db.feedQueries.selectAll() }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<Result<List<Entry>>> {
        return flowOf(Result.success(emptyList()))
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): Result<List<Entry>> {
        return runCatching {
            val entries = Collections.synchronizedList(mutableListOf<Entry>())

            withContext(Dispatchers.IO) {
                val feeds = db.feedQueries.selectAll()

                feeds.forEach { feed ->
                    runCatching {
                        entries += fetchEntries(feed)
                    }.onFailure {
                        Log.e(TAG, "Failed to fetch org.vestifeed.entries for feed: $feed", it)
                    }
                }

                val prevCachedEntryIds = db.entryQueries.selectByIds(entries.map { it.id }).map { it.id }
                entries.removeAll { prevCachedEntryIds.contains(it.id) }
            }

            entries
        }
    }

    private suspend fun fetchEntries(feed: Feed): List<Entry> {
        val url = feed.links.firstOrNull { it.rel == AtomLinkRel.Self }?.href
            ?: feed.links.firstOrNull()?.href
            ?: throw Exception("No links found for feed")
        val request = Request.Builder().url(url).build()

        val response = runCatching {
            httpClient.newCall(request).await()
        }.getOrElse {
            throw it
        }

        response.use {
            if (!response.isSuccessful) return emptyList()

            val feedResult = runCatching {
                feed(response.body!!.byteStream(), response.header("content-type") ?: "")
            }.getOrElse {
                throw it
            }

            return when (feedResult) {
                is FeedResult.Success -> {
                    when (val parsedFeed = feedResult.feed) {
                        is AtomFeed -> {
                            parsedFeed.entries
                                .mapNotNull { atomEntry ->
                                    runCatching {
                                        atomEntry.toEntry(feed.id)
                                    }.onFailure {
                                        Log.e(TAG, "Failed to parse Atom org.vestifeed.entry: $atomEntry", it)
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
                                        Log.e(TAG, "Failed to parse RSS item: $rssItem", it)
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

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean
    ): Result<Unit> {
        return Result.success(Unit)
    }

    private fun ParsedFeed.toFeed(feedUrl: HttpUrl): Feed {
        return when (this) {
            is AtomFeed -> {
                val selfLink = links.single { it.rel == AtomLinkRel.Self }
                val links = links.map { it.toLink(feedId = selfLink.href, entryId = null) }

                val feed = Feed(
                    id = selfLink.href,
                    links = links,
                    title = title,
                    extOpenEntriesInBrowser = false,
                    extBlockedWords = "",
                    extShowPreviewImages = null,
                )

                feed
            }

            is RssFeed -> {
                val selfLink = Link(
                    feedId = channel.link,
                    entryId = null,
                    href = feedUrl,
                    rel = AtomLinkRel.Self,
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
                    rel = AtomLinkRel.Alternate,
                    type = null,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )

                Feed(
                    id = channel.link,
                    links = listOf(selfLink, alternateLink),
                    title = channel.title,
                    extOpenEntriesInBrowser = false,
                    extBlockedWords = "",
                    extShowPreviewImages = null,
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
            rel = rel,
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
            extNextcloudGuidHash = "",
            extCommentsUrl = "",
            extOpenGraphImageChecked = false,
            extOpenGraphImageUrl = "",
            extOpenGraphImageWidth = 0,
            extOpenGraphImageHeight = 0,
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
                feedId = null,
                entryId = id,
                href = enclosure!!.url.toHttpUrlOrNull()!!,
                rel = AtomLinkRel.Enclosure,
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
            extRead = false,
            extReadSynced = true,
            extBookmarked = false,
            extBookmarkedSynced = true,
            extNextcloudGuidHash = "",
            extCommentsUrl = "",
            extOpenGraphImageChecked = false,
            extOpenGraphImageUrl = "",
            extOpenGraphImageWidth = 0,
            extOpenGraphImageHeight = 0,
        )
    }

    private fun sha256(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(string.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun Date.toIsoString(): String = ISO.format(this)

    private fun ParsedFeed.getEntries(feedId: String): List<Entry> {
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
        private val TAG = "api"

        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}