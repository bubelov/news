package org.vestifeed.api.miniflux

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.vestifeed.api.Api
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.Link
import org.vestifeed.http.executeAsync
import org.vestifeed.parser.AtomLinkRel
import java.io.IOException
import java.time.OffsetDateTime

class MinifluxApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) : Api {
    companion object {
        val JSON = "application/json".toMediaType()
    }

    data class MinifluxFeed(
        val id: Long,
        val title: String,
        val feedUrl: String,
        val siteUrl: String,
    )

    data class EntriesPayload(
        val total: Long,
        val entries: List<EntryJson>,
    )

    data class EntryJson(
        val id: Long,
        val feed_id: Long,
        val status: String,
        val title: String,
        val url: String,
        val comments_url: String,
        val published_at: String,
        val created_at: String,
        val changed_at: String,
        val content: String,
        val author: String,
        val starred: Boolean,
        val enclosures: List<EntryEnclosureJson>?,
    )

    data class EntryEnclosureJson(
        val id: Long,
        val user_id: Long,
        val entry_id: Long,
        val url: String,
        val mime_type: String,
        val size: Long,
    )

    private suspend fun getFeed(id: Long): MinifluxFeed {
        // https://miniflux.app/docs/api.html#endpoint-get-feed
        val req = Request.Builder().url(
            baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(id.toString()).build()
        ).get().build()
        val res = client.newCall(req).executeAsync()
        if (res.code == 200) {
            return JsonParser.parseString(res.body.string()).asJsonObject.toMinifluxFeed()
        } else {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    override suspend fun addFeed(url: HttpUrl): Api.AddFeedResult {
        // https://miniflux.app/docs/api.html#endpoint-create-feed
        val args = JsonObject().apply { add("feed_url", JsonPrimitive(url.toString())) }
        val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("feeds").build())
            .post(args.toString().toRequestBody(JSON)).build()
        val res = client.newCall(req).executeAsync()
        if (res.code == 201) {
            val body = JsonParser.parseString(res.body.string()).asJsonObject
            val feedId = body["feed_id"].asLong
            val (feed, links) = getFeed(feedId).toVestiFeed()
            return Api.AddFeedResult(
                feed = feed,
                feedLinks = links,
                entries = emptyList(),
            )
        } else {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        // https://miniflux.app/docs/api.html#endpoint-get-feeds
        val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("feeds").build()).get()
            .build()
        val res = client.newCall(req).executeAsync()
        if (res.code == 200) {
            // todo pass links
            return JsonParser.parseString(res.body.string()).asJsonArray.map { it.asJsonObject }
                .map { it.toMinifluxFeed() }.map { it.toVestiFeed().first }
        } else {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        // https://miniflux.app/docs/api.html#endpoint-update-feed
        val args = JsonObject().apply { add("title", JsonPrimitive(newTitle)) }
        val req = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(feedId).build())
            .put(args.toString().toRequestBody(JSON)).build()
        val res = client.newCall(req).executeAsync()
        return if (res.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("unexpected response code ${res.code}"))
        }
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        // https://miniflux.app/docs/api.html#endpoint-remove-feed
        val req = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(feedId).build())
            .delete().build()
        val res = client.newCall(req).executeAsync()
        return if (res.code == 204) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("unexpected response code ${res.code}"))
        }
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>> =
        flow {
            val currentBatch = mutableSetOf<EntryJson>()
            val batchSize = 10L
            var oldestEntryId = Long.MAX_VALUE

            while (true) {
                val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
                urlBuilder.addQueryParameter("order", "id")
                urlBuilder.addQueryParameter("direction", "desc")
                urlBuilder.addQueryParameter("before_entry_id", oldestEntryId.toString())
                urlBuilder.addQueryParameter("limit", batchSize.toString())
                if (!includeReadEntries) {
                    urlBuilder.addQueryParameter("status", "unread")
                }
                val req = Request.Builder().url(urlBuilder.build()).get().build()
                val rawRes = client.newCall(req).executeAsync()
                if (!rawRes.isSuccessful) {
                    throw IOException("http request failed with response code ${rawRes.code}")
                }
                val body = rawRes.body.string()
                val res = JsonParser.parseString(body).asJsonObject.toEntriesPayload()
                currentBatch += res.entries

                val mappedCurrentBatch = currentBatch.map { it.toEntry() }
                emit(mappedCurrentBatch)

                if (currentBatch.size < batchSize) {
                    break
                } else {
                    oldestEntryId = currentBatch.minOfOrNull { it.id } ?: 0L
                    currentBatch.clear()
                }
            }

            val starredUrlBuilder = baseUrl.newBuilder().addPathSegment("entries")
            starredUrlBuilder.addQueryParameter("starred", "1")
            starredUrlBuilder.addQueryParameter("limit", "0")
            val starredReq = Request.Builder().url(starredUrlBuilder.build()).get().build()
            val starredRes = client.newCall(starredReq).executeAsync()
            if (starredRes.isSuccessful) {
                val starredBody = starredRes.body.string()
                val starredPayload =
                    JsonParser.parseString(starredBody).asJsonObject.toEntriesPayload()
                if (starredPayload.entries.isNotEmpty()) {
                    currentBatch += starredPayload.entries
                    val mappedCurrentBatch = currentBatch.map { it.toEntry() }
                    emit(mappedCurrentBatch)
                }
            } else {
                throw IOException("http request failed with response code ${starredRes.code}")
            }
        }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>> {
        val changedAfter = lastSync?.toEpochSecond() ?: 0L
        val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
        urlBuilder.addQueryParameter("order", "id")
        urlBuilder.addQueryParameter("changed_after", changedAfter.toString())
        urlBuilder.addQueryParameter("limit", "0")
        val req = Request.Builder().url(urlBuilder.build()).get().build()
        val rawRes = client.newCall(req).executeAsync()
        if (rawRes.isSuccessful) {
            val body = rawRes.body.string()
            val res = JsonParser.parseString(body).asJsonObject.toEntriesPayload()
            return res.entries.map { it.toEntry() }
        } else {
            throw IOException("http request failed with response code ${rawRes.code}")
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        // https://miniflux.app/docs/api.html#endpoint-update-entries
        val args = JsonObject().apply {
            add("entry_ids", JsonArray().apply { entriesIds.forEach { add(it.toLong()) } })
            add("status", JsonPrimitive(if (read) "read" else "unread"))
        }
        val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("entries").build())
            .put(args.toString().toRequestBody(JSON)).build()
        val res = client.newCall(req).executeAsync()
        if (!res.isSuccessful || res.code != 204) {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryQueries.EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        entries.forEach { entry ->
            val req = Request.Builder().url(
                baseUrl.newBuilder().addPathSegment("entries").addPathSegment(entry.id)
                    .addPathSegment("bookmark").build()
            ).put(ByteArray(0).toRequestBody(null, 0, 0)).build()
            val rawRes = client.newCall(req).executeAsync()
            if (!rawRes.isSuccessful) {
                throw IOException("http request failed with response code ${rawRes.code}")
            }
        }
    }

    private fun EntryJson.toEntry(): Pair<Entry, List<Link>> {
        val links = mutableListOf<Link>()

        if (url.isNotBlank()) {
            links += Link(
                id = null,
                feedId = null,
                entryId = id.toString(),
                href = url,
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        enclosures?.forEach { enclosure ->
            links += Link(
                id = null,
                feedId = null,
                entryId = id.toString(),
                href = enclosure.url,
                rel = AtomLinkRel.Enclosure,
                type = enclosure.mime_type,
                hreflang = null,
                title = null,
                length = enclosure.size,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Pair(
            Entry(
                contentType = "html",
                contentSrc = "",
                contentText = content,
                summary = null,
                id = id.toString(),
                feedId = feed_id.toString(),
                title = title,
                published = OffsetDateTime.parse(published_at),
                updated = OffsetDateTime.parse(changed_at),
                authorName = author,
                extRead = status == "read",
                extReadSynced = true,
                extBookmarked = starred,
                extBookmarkedSynced = true,
                extCommentsUrl = comments_url,
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), links
        )
    }

    private fun JsonObject.toEntriesPayload(): EntriesPayload {
        val total = if (has("total") && !this["total"].isJsonNull) this["total"].asLong else 0
        val entriesArray = getAsJsonArray("entries") ?: JsonArray()
        val entries = entriesArray.map { it.asJsonObject.toEntryJson() }
        return EntriesPayload(
            total = total,
            entries = entries,
        )
    }

    private fun JsonObject.toEntryJson(): EntryJson {
        return EntryJson(
            id = this["id"].asLong,
            feed_id = this["feed_id"].asLong,
            status = this["status"].asString,
            title = this["title"].asString,
            url = this["url"].asString,
            comments_url = if (has("comments_url") && !this["comments_url"].isJsonNull) this["comments_url"].asString else "",
            published_at = if (has("published_at") && !this["published_at"].isJsonNull) this["published_at"].asString else "",
            created_at = if (has("created_at") && !this["created_at"].isJsonNull) this["created_at"].asString else "",
            changed_at = if (has("changed_at") && !this["changed_at"].isJsonNull) this["changed_at"].asString else "",
            content = if (has("content") && !this["content"].isJsonNull) this["content"].asString else "",
            author = if (has("author") && !this["author"].isJsonNull) this["author"].asString else "",
            starred = if (has("starred") && !this["starred"].isJsonNull) this["starred"].asBoolean else false,
            enclosures = if (has("enclosures") && !this["enclosures"].isJsonNull) {
                this["enclosures"].asJsonArray.map { it.asJsonObject.toEntryEnclosureJson() }
            } else null,
        )
    }

    private fun JsonObject.toEntryEnclosureJson(): EntryEnclosureJson {
        return EntryEnclosureJson(
            id = this["id"].asLong,
            user_id = this["user_id"].asLong,
            entry_id = this["entry_id"].asLong,
            url = this["url"].asString,
            mime_type = this["mime_type"].asString,
            size = this["size"].asLong,
        )
    }

    private fun JsonObject.toMinifluxFeed(): MinifluxFeed {
        return MinifluxFeed(
            id = this["id"].asLong,
            title = if (has("title") && !this["title"].isJsonNull) this["title"].asString else "",
            feedUrl = if (has("feed_url") && !this["feed_url"].isJsonNull) this["feed_url"].asString else "",
            siteUrl = if (has("site_url") && !this["site_url"].isJsonNull) this["site_url"].asString else "",
        )
    }

    private fun MinifluxFeed.toVestiFeed(): Pair<Feed, List<Link>> {
        val feedId = id.toString()

        val selfLink = Link(
            id = null,
            feedId = feedId,
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
            id = null,
            feedId = feedId,
            entryId = null,
            href = siteUrl,
            rel = AtomLinkRel.Alternate,
            type = "text/html",
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
        val feed = Feed(
            id = feedId,
            title = title,
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        return Pair(feed, listOf(selfLink, alternateLink))
    }
}