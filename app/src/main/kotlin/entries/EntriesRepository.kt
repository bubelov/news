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
    private val entryQueries: EntryQueries,
    private val api: NewsApi,
) {

    fun selectAll(): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectAll().asFlow().mapToList()
    }

    fun selectById(entryId: String): Flow<Entry?> {
        return entryQueries.selectById(entryId).asFlow().mapToOneOrNull()
    }

    fun selectByFeedId(feedId: String): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectByFeedId(feedId).asFlow().mapToList()
    }

    fun selectByReadAndBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectByReadAndBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByReadOrBookmarked(
        read: Boolean,
        bookmarked: Boolean,
    ): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectByReadOrBookmarked(read, bookmarked).asFlow().mapToList()
    }

    fun selectByRead(read: Boolean): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectByRead(read).asFlow().mapToList()
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) {
        withContext(Dispatchers.Default) {
            entryQueries.updateReadByFeedId(read, feedId)
        }
    }

    suspend fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        withContext(Dispatchers.Default) {
            entryQueries.updateReadByBookmarked(read = read, bookmarked = bookmarked)
        }
    }

    suspend fun setRead(id: String, read: Boolean, readSynced: Boolean) {
        withContext(Dispatchers.Default) {
            entryQueries.updateReadAndReadSynced(
                id = id,
                read = read,
                readSynced = readSynced,
            )
        }
    }

    fun getBookmarked(): Flow<List<EntryWithoutContent>> {
        return entryQueries.selectByBookmarked(true).asFlow().mapToList()
    }

    suspend fun setBookmarked(
        id: String,
        bookmarked: Boolean,
        bookmarkedSynced: Boolean,
    ) {
        withContext(Dispatchers.Default) {
            entryQueries.updateBookmarkedAndBookmaredSynced(
                id = id,
                bookmarked = bookmarked,
                bookmarkedSynced = bookmarkedSynced,
            )
        }
    }

    suspend fun setOgImageChecked(id: String, checked: Boolean) {
        withContext(Dispatchers.Default) {
            entryQueries.updateOgImageChecked(checked, id)
        }
    }

    suspend fun setOgImage(url: String, width: Long, height: Long, id: String) {
        withContext(Dispatchers.Default) {
            entryQueries.updateOgImage(url, width, height, id)
        }
    }

    fun getUnreadCount(feedId: String): Flow<Long> {
        return entryQueries.selectUnreadCount(feedId).asFlow().mapToOne()
    }

    private fun getMaxId(): Flow<String?> {
        return entryQueries.selectMaxId().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    private fun getMaxUpdated(): Flow<String?> {
        return entryQueries.selectMaxUpdaded().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    fun selectByQuery(query: String): Flow<List<Entry>> {
        return entryQueries.selectByQuery(query).asFlow().mapToList()
    }

    fun selectByQueryAndBookmarked(query: String, bookmarked: Boolean): Flow<List<Entry>> {
        return entryQueries.selectByQueryAndBookmarked(bookmarked, query).asFlow().mapToList()
    }

    fun selectByQueryAndFeedId(query: String, feedId: String): Flow<List<Entry>> {
        return entryQueries.selectByQueryAndFeedId(feedId, query).asFlow().mapToList()
    }

    fun selectCount() = entryQueries.selectCount().asFlow().mapToOne()

    suspend fun deleteByFeedId(feedId: String) {
        withContext(Dispatchers.Default) {
            entryQueries.deleteByFeedId(feedId)
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
                entryQueries.transaction {
                    batch.forEach { entryWithLinks ->
                        entryQueries.insertOrReplace(entryWithLinks.first.postProcess())
                    }
                }
            }
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.Default) {
            val unsyncedEntries = entryQueries.selectByReadSynced(false).executeAsList()

            if (unsyncedEntries.isEmpty()) {
                return@withContext
            }

            val unsyncedReadEntries = unsyncedEntries.filter { it.read }

            if (unsyncedReadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedReadEntries.map { it.id },
                    read = true,
                )

                entryQueries.transaction {
                    unsyncedReadEntries.forEach {
                        entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }

            val unsyncedUnreadEntries = unsyncedEntries.filter { !it.read }

            if (unsyncedUnreadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedUnreadEntries.map { it.id },
                    read = false,
                )

                entryQueries.transaction {
                    unsyncedUnreadEntries.forEach {
                        entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() {
        withContext(Dispatchers.Default) {
            val notSyncedEntries = entryQueries.selectByBookmarkedSynced(false).executeAsList()

            if (notSyncedEntries.isEmpty()) {
                return@withContext
            }

            val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.bookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                entryQueries.transaction {
                    notSyncedBookmarkedEntries.forEach {
                        entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }

            val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.bookmarked }

            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                entryQueries.transaction {
                    notSyncedNotBookmarkedEntries.forEach {
                        entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
        feeds: List<Feed>,
    ): Int {
        return withContext(Dispatchers.Default) {
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

            entryQueries.transaction {
                entries.forEach { newEntryWithLinks ->
                    val feed = feeds.firstOrNull { it.id == newEntryWithLinks.first.feedId }
                    entryQueries.insertOrReplace(newEntryWithLinks.first.postProcess(feed))
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