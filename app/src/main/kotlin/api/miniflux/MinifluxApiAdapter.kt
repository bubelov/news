package api.miniflux

import android.util.Log
import api.Api
import co.appreactor.feedk.AtomLinkRel
import db.Entry
import db.EntryWithoutContent
import db.Feed
import db.Link
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime

class MinifluxApiAdapter(
    private val api: MinifluxApi,
) : Api {

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
        return runCatching {
            val categories = api.getCategories()

            if (categories.isEmpty()) {
                return Result.failure(Exception("You have no categories"))
            }

            // Catch-all category always has the lowest id
            val category = categories.minByOrNull { it.id }!!

            val response = api.postFeed(
                PostFeedArgs(
                    feed_url = url.toString(),
                    category_id = category.id,
                )
            )

            Pair(api.getFeed(response.feed_id).toFeed()!!, emptyList())
        }
    }

    override suspend fun getFeeds(): Result<List<Feed>> {
        return runCatching {
            api.getFeeds().mapNotNull {
                try {
                    it.toFeed()
                } catch (e: Exception) {
                    Log.w("MinifluxApiAdapter", "Failed to parse feed ${it.feed_url}", e)
                    null
                }
            }
        }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return runCatching { api.putFeed(feedId.toLong(), PutFeedArgs(newTitle)) }
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return runCatching { api.deleteFeed(feedId.toLong()) }
    }

    override suspend fun getEntries(
        includeReadEntries: Boolean,
    ): Flow<Result<List<Entry>>> {
        return flow {
            runCatching {
                var totalFetched = 0L
                val currentBatch = mutableSetOf<EntryJson>()
                val batchSize = 250L
                var oldestEntryId = Long.MAX_VALUE

                while (true) {
                    val entries = api.getEntriesBeforeEntry(
                        status = if (includeReadEntries) "" else "unread",
                        entryId = oldestEntryId,
                        limit = batchSize,
                    )

                    currentBatch += entries.entries
                    totalFetched += currentBatch.size
                    emit(Result.success(currentBatch.map { it.toEntry() }))

                    if (currentBatch.size < batchSize) {
                        break
                    } else {
                        oldestEntryId = currentBatch.minOfOrNull { it.id }?.toLong() ?: 0L
                        currentBatch.clear()
                    }
                }

                val starredEntries = api.getStarredEntries()

                if (starredEntries.entries.isNotEmpty()) {
                    currentBatch += starredEntries.entries
                    totalFetched += currentBatch.size
                    emit(Result.success(currentBatch.map { it.toEntry() }))
                    currentBatch.clear()
                }
            }.getOrElse {
                emit(Result.failure(it))
                return@flow
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): Result<List<Entry>> {
        return runCatching {
            api.getEntriesAfterEntry(
                afterEntryId = maxEntryId?.toLong() ?: 0,
                limit = 0,
            ).entries.map { it.toEntry() }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean): Result<Unit> {
        return runCatching {
            api.putEntryStatus(
                PutStatusArgs(
                    entry_ids = entriesIds.map { it.toLong() },
                    status = if (read) "read" else "unread",
                )
            )
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ): Result<Unit> {
        return runCatching {
            entries.forEach { api.putEntryBookmark(it.id.toLong()) }
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        val feedId = id?.toString() ?: return null

        val selfLink = Link(
            feedId = feedId,
            entryId = null,
            href = feed_url.toHttpUrl(),
            rel = AtomLinkRel.Self,
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        val alternateLink = try {
            Link(
                feedId = feedId,
                entryId = null,
                href = site_url.toHttpUrl(),
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        } catch (e: Exception) {
            Log.d("MinifluxApiAdapter", "Failed to parse alternate link for feed $feed_url", e)
            null
        }

        return Feed(
            id = feedId,
            links = listOfNotNull(selfLink, alternateLink),
            title = title,
            ext_open_entries_in_browser = false,
            ext_blocked_words = "",
            ext_show_preview_images = null,
        )
    }

    private fun EntryJson.toEntry(): Entry {
        val links = mutableListOf<Link>()

        links += Link(
            feedId = null,
            entryId = id.toString(),
            href = url.toHttpUrl(),
            rel = AtomLinkRel.Alternate,
            type = "text/html",
            hreflang = "",
            title = "",
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        enclosures?.forEach {
            links += Link(
                feedId = null,
                entryId = id.toString(),
                href = it.url.toHttpUrl(),
                rel = AtomLinkRel.Enclosure,
                type = it.mime_type,
                hreflang = "",
                title = "",
                length = it.size,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Entry(
            content_type = "html",
            content_src = "",
            content_text = content,
            links = links,
            summary = null,
            id = id.toString(),
            feed_id = feed_id.toString(),
            title = title,
            published = OffsetDateTime.parse(published_at),
            updated = OffsetDateTime.parse(changed_at),
            author_name = author,
            ext_read = status == "read",
            ext_read_synced = true,
            ext_bookmarked = starred,
            ext_bookmarked_synced = true,
            ext_nc_guid_hash = "",
            ext_comments_url = comments_url,
            ext_og_image_checked = false,
            ext_og_image_url = "",
            ext_og_image_width = 0,
            ext_og_image_height = 0,
        )
    }
}