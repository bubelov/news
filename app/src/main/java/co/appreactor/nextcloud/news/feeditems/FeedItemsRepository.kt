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

    fun deleteByFeedId(feedId: Long) {
        db.deleteByFeedId(feedId)
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
        val unsyncedItems = all().first().filter {
            !it.unreadSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedReadItems = unsyncedItems.filterNot { it.unread }

        if (unsyncedReadItems.isNotEmpty()) {
            val response = api.putRead(PutReadArgs(
                unsyncedReadItems.map { it.id }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedReadItems.forEach {
                        db.updateUnreadSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedUnreadItems = unsyncedItems.filter { it.unread }

        if (unsyncedUnreadItems.isNotEmpty()) {
            val response = api.putUnread(PutReadArgs(
                unsyncedUnreadItems.map { it.id }
            )).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedUnreadItems.forEach {
                        db.updateUnreadSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun syncStarredFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = all().first().filter {
            !it.starredSynced
        }

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedStarredItems = unsyncedItems.filter { it.starred }

        if (unsyncedStarredItems.isNotEmpty()) {
            val response = api.putStarred(PutStarredArgs(unsyncedStarredItems.map {
                PutStarredArgsItem(
                    it.feedId,
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedStarredItems.forEach {
                        db.updateStarredSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }

        val unsyncedUnstarredItems = unsyncedItems.filter { !it.starred }

        if (unsyncedUnstarredItems.isNotEmpty()) {
            val response = api.putUnstarred(PutStarredArgs(unsyncedUnstarredItems.map {
                PutStarredArgsItem(
                    it.feedId,
                    it.guidHash
                )
            })).execute()

            if (response.isSuccessful) {
                db.transaction {
                    unsyncedUnstarredItems.forEach {
                        db.updateStarredSynced(true, it.id)
                    }
                }
            } else {
                throw Exception(response.errorBody()?.string() ?: "Unknown error")
            }
        }
    }

    suspend fun fetchNewAndUpdatedItems() = withContext(Dispatchers.IO) {
        val mostRecentItem = all().firstOrNull()?.maxByOrNull { it.lastModified } ?: return@withContext

        val newAndUpdatedItems = api.getNewAndUpdatedItems(mostRecentItem.lastModified + 1).execute().body()!!.items

        db.transaction {
            newAndUpdatedItems.forEach {
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