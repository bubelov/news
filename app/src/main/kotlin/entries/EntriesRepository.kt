package entries

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Entry
import db.EntryQueries
import db.EntryWithoutContent
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class EntriesRepository(
    private val api: NewsApi,
    private val db: EntryQueries,
) {

    fun selectAll(): Flow<List<EntryWithoutContent>> {
        return db.selectAll().asFlow().mapToList()
    }

    fun selectById(entryId: String): Flow<Entry?> {
        return db.selectById(entryId).asFlow().mapToOneOrNull()
    }

    fun selectByFeedId(feedId: String): Flow<List<EntryWithoutContent>> {
        return db.selectByFeedId(feedId).asFlow().mapToList()
    }

    fun selectByReadAndBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutContent>> {
        return db.selectByReadAndBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByReadOrBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutContent>> {
        return db.selectByReadOrBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByRead(read: Boolean): Flow<List<EntryWithoutContent>> {
        return db.selectByRead(read).asFlow().mapToList()
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) {
        withContext(Dispatchers.Default) {
            db.updateReadByFeedId(read, feedId)
        }
    }

    suspend fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        withContext(Dispatchers.Default) {
            db.updateReadByBookmarked(read = read, bookmarked = bookmarked)
        }
    }

    suspend fun setRead(id: String, read: Boolean, readSynced: Boolean) {
        withContext(Dispatchers.Default) {
            db.updateReadAndReadSynced(
                id = id,
                read = read,
                readSynced = readSynced,
            )
        }
    }

    fun getBookmarked(): Flow<List<EntryWithoutContent>> {
        return db.selectByBookmarked(true).asFlow().mapToList()
    }

    suspend fun setBookmarked(
        id: String,
        bookmarked: Boolean,
        bookmarkedSynced: Boolean,
    ) {
        withContext(Dispatchers.Default) {
            db.updateBookmarkedAndBookmaredSynced(
                id = id,
                bookmarked = bookmarked,
                bookmarkedSynced = bookmarkedSynced,
            )
        }
    }

    suspend fun setOgImageChecked(id: String, checked: Boolean) {
        withContext(Dispatchers.Default) {
            db.updateOgImageChecked(checked, id)
        }
    }

    suspend fun setOgImage(url: String, width: Long, height: Long, id: String) {
        withContext(Dispatchers.Default) {
            db.updateOgImage(url, width, height, id)
        }
    }

    fun getUnreadCount(feedId: String): Flow<Long> {
        return db.selectUnreadCount(feedId).asFlow().mapToOne()
    }

    private fun getMaxId(): Flow<String?> {
        return db.selectMaxId().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    private fun getMaxUpdated(): Flow<String?> {
        return db.selectMaxUpdaded().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    fun selectByQuery(query: String): Flow<List<Entry>> {
        return db.selectByQuery(query).asFlow().mapToList()
    }

    fun selectByQueryAndBookmarked(query: String, bookmarked: Boolean): Flow<List<Entry>> {
        return db.selectByQueryAndBookmarked(bookmarked, query).asFlow().mapToList()
    }

    fun selectByQueryAndFeedId(query: String, feedId: String): Flow<List<Entry>> {
        return db.selectByQueryAndFeedId(feedId, query).asFlow().mapToList()
    }

    fun selectCount() = db.selectCount().asFlow().mapToOne()

    suspend fun deleteByFeedId(feedId: String) {
        withContext(Dispatchers.Default) {
            db.deleteByFeedId(feedId)
        }
    }

    suspend fun syncAll(): Flow<SyncProgress> = flow {
        emit(SyncProgress(0L))

        var entriesLoaded = 0L
        emit(SyncProgress(entriesLoaded))

        api.getEntries(false).collect { batch ->
            entriesLoaded += batch.size
            emit(SyncProgress(entriesLoaded))

            withContext(Dispatchers.Default) {
                db.transaction {
                    batch.forEach {
                        db.insertOrReplace(it.postProcess())
                    }
                }
            }
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.Default) {
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
    }

    suspend fun syncBookmarkedEntries() {
        withContext(Dispatchers.Default) {
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
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
        feeds: List<Feed>,
    ): Int {
        return withContext(Dispatchers.Main) {
            val lastSyncInstant = if (lastEntriesSyncDateTime.isNotBlank()) {
                OffsetDateTime.parse(lastEntriesSyncDateTime)
            } else {
                null
            }

            val maxUpdated = getMaxUpdated().first()

            val maxUpdatedInstant = if (maxUpdated != null) {
                OffsetDateTime.parse(maxUpdated)
            } else {
                null
            }

            val entries = api.getNewAndUpdatedEntries(
                lastSync = lastSyncInstant,
                maxEntryId = getMaxId().first(),
                maxEntryUpdated = maxUpdatedInstant,
            )

            db.transaction {
                entries.forEach { newEntry ->
                    val feed = feeds.firstOrNull { it.id == newEntry.feedId }
                    db.insertOrReplace(newEntry.postProcess(feed))
                }
            }

            entries.size
        }
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