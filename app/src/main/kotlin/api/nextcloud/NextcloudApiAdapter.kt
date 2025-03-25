package api.nextcloud

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
import java.time.Instant
import java.time.OffsetDateTime

class NextcloudApiAdapter(
    private val api: NextcloudApi,
) : Api {

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
        return runCatching {
            val response = api.postFeed(
                PostFeedArgs(
                    url = url.toString(),
                    folderId = 0,
                )
            )

            Pair(response.feeds.single().toFeed()!!, emptyList())
        }
    }

    override suspend fun getFeeds(): Result<List<Feed>> {
        return runCatching { api.getFeeds().feeds.mapNotNull { it.toFeed() } }
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return runCatching {
            api.putFeedRename(
                id = feedId.toLong(),
                args = PutFeedRenameArgs(newTitle),
            )
        }
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return runCatching { api.deleteFeed(feedId.toLong()) }
    }

    override suspend fun getEntries(
        includeReadEntries: Boolean,
    ): Flow<Result<List<Entry>>> {
        return flow {
            var totalFetched = 0L
            val currentBatch = mutableSetOf<ItemJson>()
            val batchSize = 250L
            var oldestEntryId = 0L

            while (true) {
                val response = runCatching {
                    api.getAllItems(
                        getRead = includeReadEntries,
                        batchSize = batchSize,
                        offset = oldestEntryId,
                    )
                }.getOrElse {
                    emit(Result.failure(it))
                    return@flow
                }

                val entries = response.items
                currentBatch += entries
                totalFetched += currentBatch.size
                emit(Result.success(currentBatch.mapNotNull { it.toEntry() }))

                if (currentBatch.size < batchSize) {
                    break
                } else {
                    oldestEntryId =
                        currentBatch.minOfOrNull { it.id ?: Long.MAX_VALUE }?.toLong() ?: 0L
                    currentBatch.clear()
                }
            }
        }
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): Result<List<Entry>> {
        return runCatching {
            val lastModified = maxEntryUpdated ?: lastSync!!
            api.getNewAndUpdatedItems(lastModified.toEpochSecond() + 1).items.mapNotNull { it.toEntry() }
        }
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean): Result<Unit> {
        return runCatching {
            val ids = entriesIds.map { it.toLong() }

            if (read) {
                api.putRead(PutReadArgs(ids))
            } else {
                api.putUnread(PutReadArgs(ids))
            }
        }
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ): Result<Unit> {
        return runCatching {
            val args = PutStarredArgs(entries.map {
                PutStarredArgsItem(
                    it.feedId.toLong(),
                    it.extNextcloudGuidHash
                )
            })

            if (bookmarked) {
                api.putStarred(args)
            } else {
                api.putUnstarred(args)
            }
        }
    }

    private fun FeedJson.toFeed(): Feed? {
        val feedId = id?.toString() ?: return null

        val links = mutableListOf<Link>()

        if (!url.isNullOrBlank()) {
            links += Link(
                feedId = feedId,
                entryId = null,
                href = url.toHttpUrl(),
                rel = AtomLinkRel.Self,
                type = null,
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        if (!link.isNullOrBlank()) {
            links += Link(
                feedId = feedId,
                entryId = null,
                href = link.toHttpUrl(),
                rel = AtomLinkRel.Alternate,
                type = null,
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Feed(
            id = feedId,
            links = links,
            title = title ?: "Untitled",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
    }

    private fun ItemJson.toEntry(): Entry? {
        if (id == null) return null
        if (pubDate == null) return null
        if (lastModified == null) return null
        if (unread == null) return null
        if (starred == null) return null

        val published = Instant.ofEpochSecond(pubDate).toString()
        val updated = Instant.ofEpochSecond(lastModified).toString()

        val links = mutableListOf<Link>()

        if (!url.isNullOrBlank()) {
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
        }

        if (!enclosureLink.isNullOrBlank()) {
            links += Link(
                feedId = null,
                entryId = id.toString(),
                href = enclosureLink.toHttpUrl(),
                rel = AtomLinkRel.Enclosure,
                type = enclosureMime ?: "",
                hreflang = "",
                title = "",
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Entry(
            contentType = "html",
            contentSrc = "",
            contentText = body ?: "",
            links = links,
            summary = "",
            id = id.toString(),
            feedId = feedId?.toString() ?: "",
            title = title ?: "Untitled",
            published = OffsetDateTime.parse(published),
            updated = OffsetDateTime.parse(updated),
            authorName = author ?: "",
            extRead = !unread,
            extReadSynced = true,
            extBookmarked = starred,
            extBookmarkedSynced = true,
            extNextcloudGuidHash = guidHash ?: return null,
            extCommentsUrl = "",
            extOpenGraphImageChecked = false,
            extOpenGraphImageUrl = "",
            extOpenGraphImageWidth = 0,
            extOpenGraphImageHeight = 0,
        )
    }
}