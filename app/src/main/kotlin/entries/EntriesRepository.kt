package entries

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Entry
import db.EntryQueries
import db.EntryWithoutSummary
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class EntriesRepository(
    private val api: NewsApi,
    private val db: EntryQueries,
) {

    fun selectAll(): Flow<List<EntryWithoutSummary>> {
        return db.selectAll().asFlow().mapToList()
    }

    fun selectById(entryId: String): Flow<Entry?> {
        return db.selectById(entryId).asFlow().mapToOneOrNull()
    }

    fun selectByFeedId(feedId: String): Flow<List<EntryWithoutSummary>> {
        return db.selectByFeedId(feedId).asFlow().mapToList()
    }

    fun selectByReadAndBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutSummary>> {
        return db.selectByReadAndBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByReadOrBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutSummary>> {
        return db.selectByReadOrBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByRead(read: Boolean): Flow<List<EntryWithoutSummary>> {
        return db.selectByRead(read).asFlow().mapToList()
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) = withContext(Dispatchers.IO) {
        db.updateReadByFeedId(read, feedId)
    }

    suspend fun updateReadByBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            db.updateReadByBookmarked(read = read, bookmarked = bookmarked)
        }
    }

    fun setRead(id: String, read: Boolean) {
        db.apply {
            transaction {
                updateRead(read, id)
                updateReadSynced(false, id)
            }
        }
    }

    suspend fun getBookmarked() = withContext(Dispatchers.IO) {
        db.selectByBookmarked(true).asFlow().mapToList()
    }

    fun setBookmarked(id: String, bookmarked: Boolean) {
        db.apply {
            transaction {
                updateBookmarked(bookmarked, id)
                updateBookmarkedSynced(false, id)
            }
        }
    }

    fun setOgImageChecked(id: String, checked: Boolean) {
        db.updateOgImageChecked(checked, id)
    }

    fun setOgImage(url: String, width: Long, height: Long, id: String) {
        db.updateOgImage(url, width, height, id)
    }

    suspend fun getUnreadCount(feedId: String) = withContext(Dispatchers.IO) {
        db.selectUnreadCount(feedId).asFlow().mapToOne()
    }

    private suspend fun getMaxId() = withContext(Dispatchers.IO) {
        db.selectMaxId().executeAsOneOrNull()?.MAX
    }

    private suspend fun getMaxUpdated() = withContext(Dispatchers.IO) {
        db.selectMaxUpdaded().executeAsOneOrNull()?.MAX
    }

    suspend fun selectByQuery(query: String) = withContext(Dispatchers.IO) {
        db.selectByQuery(query).executeAsList()
    }

    suspend fun selectByQueryAndBookmarked(query: String, bookmarked: Boolean) =
        withContext(Dispatchers.IO) {
            db.selectByQueryAndBookmarked(bookmarked, query).executeAsList()
        }

    suspend fun selectByQueryAndFeedId(query: String, feedId: String) =
        withContext(Dispatchers.IO) {
            db.selectByQueryAndFeedId(feedId, query).executeAsList()
        }

    fun selectCount() = db.selectCount().asFlow().mapToOne()

    fun deleteByFeedId(feedId: String) {
        db.deleteByFeedId(feedId)
    }

    suspend fun syncAll(): Flow<SyncProgress> = flow {
        emit(SyncProgress(0L))

        var entriesLoaded = 0L
        emit(SyncProgress(entriesLoaded))

        api.getEntries(false).collect { batch ->
            entriesLoaded += batch.size
            emit(SyncProgress(entriesLoaded))

            db.transaction {
                batch.forEach {
                    db.insertOrReplace(it.postProcess())
                }
            }
        }
    }

    suspend fun syncReadEntries() = withContext(Dispatchers.IO) {
        val unsyncedEntries = db.selectByReadSynced(false).executeAsList()

        if (unsyncedEntries.isEmpty()) {
            return@withContext
        }

        val unsyncedReadEntries = unsyncedEntries.filter { it.read }

        if (unsyncedReadEntries.isNotEmpty()) {
            api.markEntriesAsRead(
                entriesIds = unsyncedReadEntries.map { it.id },
                read = true,
            )

            db.transaction {
                unsyncedReadEntries.forEach {
                    db.updateReadSynced(true, it.id)
                }
            }
        }

        val unsyncedUnreadEntries = unsyncedEntries.filter { !it.read }

        if (unsyncedUnreadEntries.isNotEmpty()) {
            api.markEntriesAsRead(
                entriesIds = unsyncedUnreadEntries.map { it.id },
                read = false,
            )

            db.transaction {
                unsyncedUnreadEntries.forEach {
                    db.updateReadSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() = withContext(Dispatchers.IO) {
        val notSyncedEntries = db.selectByBookmarkedSynced(false).executeAsList()

        if (notSyncedEntries.isEmpty()) {
            return@withContext
        }

        val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.bookmarked }

        if (notSyncedBookmarkedEntries.isNotEmpty()) {
            api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

            db.transaction {
                notSyncedBookmarkedEntries.forEach {
                    db.updateBookmarkedSynced(true, it.id)
                }
            }
        }

        val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.bookmarked }

        if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
            api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

            db.transaction {
                notSyncedNotBookmarkedEntries.forEach {
                    db.updateBookmarkedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
        feeds: List<Feed>,
    ): Int = withContext(Dispatchers.IO) {
        val lastSyncInstant = if (lastEntriesSyncDateTime.isNotBlank()) {
            OffsetDateTime.parse(lastEntriesSyncDateTime)
        } else {
            null
        }

        val maxUpdated = getMaxUpdated()

        val maxUpdatedInstant = if (maxUpdated != null) {
            OffsetDateTime.parse(maxUpdated)
        } else {
            null
        }

        val entries = api.getNewAndUpdatedEntries(
            lastSync = lastSyncInstant,
            maxEntryId = getMaxId(),
            maxEntryUpdated = maxUpdatedInstant,
        )

        db.transaction {
            entries.forEach { newEntry ->
                val feed = feeds.firstOrNull { it.id == newEntry.feedId }
                db.insertOrReplace(newEntry.postProcess(feed))
            }
        }

        return@withContext entries.size
    }

    private fun Entry.postProcess(feed: Feed? = null): Entry {
        var processedEntry = this

        if (contentText.toByteArray().size / 1024 > 250) {
            processedEntry = processedEntry.copy(contentText = "Content is too large")
        }

        feed?.blockedWords?.split(",")?.filter { it.isNotBlank() }?.forEach { word ->
            if (processedEntry.title.contains(word, ignoreCase = true)) {
                processedEntry = processedEntry.copy(read = true)
            }
        }

        return processedEntry
    }

    data class SyncProgress(val itemsSynced: Long)
}