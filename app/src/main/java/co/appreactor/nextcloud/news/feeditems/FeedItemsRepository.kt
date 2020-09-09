package co.appreactor.nextcloud.news.feeditems

import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.api.NewsApi
import co.appreactor.nextcloud.news.api.PutReadArgs
import co.appreactor.nextcloud.news.api.PutStarredArgs
import co.appreactor.nextcloud.news.api.PutStarredArgsItem
import co.appreactor.nextcloud.news.db.FeedItemQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.math.min

class FeedItemsRepository(
    private val db: FeedItemQueries,
    private val api: NewsApi
) {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        db.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun unread() = withContext(Dispatchers.IO) {
        db.findUnread().asFlow().map { it.executeAsList() }
    }

    suspend fun starred() = withContext(Dispatchers.IO) {
        db.findStarred().asFlow().map { it.executeAsList() }
    }

    suspend fun byId(id: Long) = withContext(Dispatchers.IO) {
        db.findById(id).asFlow().map { it.executeAsOneOrNull() }
    }

    suspend fun updateUnread(id: Long, unread: Boolean) = withContext(Dispatchers.IO) {
        db.updateUnread(
            unread = unread,
            id = id
        )
    }

    suspend fun updateStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        db.updateStarred(
            starred = starred,
            id = id
        )
    }

    suspend fun updateOpenGraphImageUrl(id: Long, url: String) = withContext(Dispatchers.IO) {
        db.updateOpenGraphImageUrl(
            openGraphImageUrl = url,
            id = id
        )
    }

    suspend fun updateOpenGraphImageParsingFailed(id: Long, failed: Boolean) = withContext(Dispatchers.IO) {
        db.updateOpenGraphImageParsingFailed(
            openGraphImageParsingFailed = failed,
            id = id
        )
    }

    suspend fun updateEnclosureDownloadProgress(id: Long, progress: Long?) = withContext(Dispatchers.IO) {
        db.updateEnclosureDownloadProgress(
            enclosureDownloadProgress = progress,
            id = id
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }

    suspend fun performInitialSyncIfNoData() = withContext(Dispatchers.IO) {
        Timber.d("Performing initial sync (if no data)")
        val count = db.count().executeAsOne()
        Timber.d("Records: $count")

        if (count > 0) {
            return@withContext
        }

        val unread = api.getUnreadItems().execute().body()!!
        val starred = api.getStarredItems().execute().body()!!

        db.transaction {
            (unread.items + starred.items).forEach {
                db.insertOrReplace(
                    it.copy(
                        unreadSynced = true,
                        starredSynced = true,
                        summary = getSummary(it.body),
                        openGraphImageUrl = "",
                        openGraphImageParsingFailed = false,
                        enclosureDownloadProgress = null,
                    )
                )
            }
        }
    }

    suspend fun syncUnreadFlags() = withContext(Dispatchers.IO) {
        Timber.d("Syncing unread flags")

        val unsyncedItems = all().first().filter {
            !it.unreadSynced
        }

        Timber.d("Unsynced items: ${unsyncedItems.size}")

        val unsyncedReadItems = unsyncedItems.filterNot { it.unread }
        val unsyncedUnreadItems = unsyncedItems.filter { it.unread }
        Timber.d("Of them, read: ${unsyncedReadItems.size}")
        Timber.d("Of them, unread: ${unsyncedUnreadItems.size}")

        val markAsReadResponse = api.putRead(PutReadArgs(
            unsyncedReadItems.map { it.id }
        )).execute()

        if (markAsReadResponse.isSuccessful) {
            db.transaction {
                unsyncedReadItems.forEach {
                    db.updateUnreadSynced(true, it.id)
                }
            }
        } else {
            throw Exception(markAsReadResponse.message())
        }

        val markAsUnreadResponse = api.putUnread(PutReadArgs(
            unsyncedUnreadItems.map { it.id }
        )).execute()

        if (markAsUnreadResponse.isSuccessful) {
            db.transaction {
                unsyncedUnreadItems.forEach {
                    db.updateUnreadSynced(true, it.id)
                }
            }
        } else {
            throw Exception(markAsUnreadResponse.message())
        }

        Timber.d("Finished syncing unread flags")
    }

    suspend fun syncStarredFlags() = withContext(Dispatchers.IO) {
        Timber.d("Syncing starred flags")

        val unsyncedItems = all().first().filter {
            !it.starredSynced
        }

        Timber.d("Unsynced items: ${unsyncedItems.size}")

        val unsyncedStarredItems = unsyncedItems.filter { it.starred }
        val unsyncedUnstarredItems = unsyncedItems.filter { !it.starred }
        Timber.d("Of them, starred: ${unsyncedStarredItems.size}")
        Timber.d("Of them, unread: ${unsyncedUnstarredItems.size}")

        api.putStarred(PutStarredArgs(unsyncedStarredItems.map {
            PutStarredArgsItem(
                it.feedId,
                it.guidHash
            )
        })).execute()

        db.transaction {
            unsyncedStarredItems.forEach {
                db.updateStarredSynced(true, it.id)
            }
        }

        api.putUnstarred(PutStarredArgs(unsyncedUnstarredItems.map {
            PutStarredArgsItem(
                it.feedId,
                it.guidHash
            )
        })).execute()

        db.transaction {
            unsyncedUnstarredItems.forEach {
                db.updateStarredSynced(true, it.id)
            }
        }

        Timber.d("Finished syncing starred flags")
    }

    suspend fun fetchNewAndUpdatedItems() = withContext(Dispatchers.IO) {
        Timber.d("Syncing new and updated items")

        val mostRecentItem = all().firstOrNull()?.maxByOrNull { it.lastModified }

        if (mostRecentItem == null) {
            Timber.d("Cache is empty, cancelling")
            return@withContext
        }

        val items = api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1).execute().body()!!.items
        Timber.d("New and updated items: ${items.size}")

        db.transaction {
            items.forEach {
                db.insertOrReplace(
                    it.copy(
                        unreadSynced = true,
                        starredSynced = true,
                        summary = getSummary(it.body),
                        openGraphImageUrl = "",
                        openGraphImageParsingFailed = false,
                        enclosureDownloadProgress = null
                    )
                )
            }
        }

        Timber.d("Finished syncing new and updated items")
    }

    private fun getSummary(body: String): String {
        val replaceImgPattern = Pattern.compile("<img([\\w\\W]+?)>", Pattern.DOTALL)
        val bodyWithoutImg = body.replace(replaceImgPattern.toRegex(), "")
        val parsedBody =
            HtmlCompat.fromHtml(bodyWithoutImg, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().replace("\n", " ")

        return buildString {
            append(parsedBody.substring(0, min(parsedBody.length - 1, SUMMARY_MAX_LENGTH)))

            if (length == SUMMARY_MAX_LENGTH) {
                append("…")
            }
        }
    }
}