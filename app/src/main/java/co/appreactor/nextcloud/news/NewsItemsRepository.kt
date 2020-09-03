package co.appreactor.nextcloud.news

import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.NewsItemQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.math.min

class NewsItemsRepository(
    private val cache: NewsItemQueries,
    private val api: NewsApi
) {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        cache.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun byId(id: Long) = withContext(Dispatchers.IO) {
        cache.findById(id).asFlow().map { it.executeAsOneOrNull() }
    }

    suspend fun updateUnread(id: Long, unread: Boolean) = withContext(Dispatchers.IO) {
        cache.updateUnread(
            unread = unread,
            id = id
        )
    }

    suspend fun updateStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        cache.updateStarred(
            starred = starred,
            id = id
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.deleteAll()
    }

    suspend fun performInitialSyncIfNoData() = withContext(Dispatchers.IO) {
        Timber.d("Performing initial sync (if no data)")
        val count = cache.count().executeAsOne()
        Timber.d("Records: $count")

        if (count > 0) {
            return@withContext
        }

        val unread = api.getUnreadItems().execute().body()!!
        val starred = api.getStarredItems().execute().body()!!

        cache.transaction {
            (unread.items + starred.items).forEach {
                cache.insertOrReplace(
                    it.copy(
                        unreadSynced = true,
                        starredSynced = true,
                        summary = getSummary(it.body)
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

        api.markAsRead(PutReadArgs(
            unsyncedReadItems.map { it.id }
        )).execute()

        cache.transaction {
            unsyncedReadItems.forEach {
                cache.updateUnreadSynced(true, it.id)
            }
        }

        api.markAsUnread(
            PutReadArgs(
                unsyncedUnreadItems.map { it.id }
            )
        ).execute()

        cache.transaction {
            unsyncedUnreadItems.forEach {
                cache.updateUnreadSynced(true, it.id)
            }
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

        api.markAsStarred(PutStarredArgs(unsyncedStarredItems.map {
            PutStarredArgsItem(
                it.feedId,
                it.guidHash
            )
        })).execute()

        cache.transaction {
            unsyncedStarredItems.forEach {
                cache.updateStarredSynced(true, it.id)
            }
        }

        api.markAsUnstarred(PutStarredArgs(unsyncedUnstarredItems.map {
            PutStarredArgsItem(
                it.feedId,
                it.guidHash
            )
        })).execute()

        cache.transaction {
            unsyncedUnstarredItems.forEach {
                cache.updateStarredSynced(true, it.id)
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

        val items =
            api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1).execute().body()!!.items
        Timber.d("New and updated items: ${items.size}")

        cache.transaction {
            items.forEach {
                cache.insertOrReplace(
                    it.copy(
                        unreadSynced = true,
                        starredSynced = true,
                        summary = getSummary(it.body)
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