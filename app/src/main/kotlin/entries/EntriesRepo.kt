package entries

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import api.Api
import db.Db
import db.EntriesAdapterRow
import db.Entry
import db.Feed
import db.SelectByQuery
import db.ShortEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.time.OffsetDateTime

@Single
class EntriesRepo(
    private val api: Api,
    private val db: Db,
) {

    fun insertOrReplace(entry: Entry) {
        db.entryQueries.insertOrReplace(entry)
    }

    fun selectAll(): List<Entry> {
        return emptyList()
    }

    suspend fun insertOrReplace(entries: List<Entry>) {
        withContext(Dispatchers.IO) {
            db.transaction {
                entries.forEach { entry ->
                    val postProcessedEntry = entry.postProcess()
                    insertOrReplace(postProcessedEntry)
                }
            }
        }
    }

    fun selectAllLinksPublishedAndTitle(): Flow<List<ShortEntry>> {
        return flowOf(db.entryQueries.selectAllLinksPublishedAndTitle())
    }

    fun selectById(entryId: String): Flow<Entry?> {
        return flowOf(db.entryQueries.selectById(entryId))
    }

    fun selectByFeedIdAndReadAndBookmarked(
        feedId: String,
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
        return flowOf(db.entryQueries.selectByFeedIdAndReadAndBookmarked(feedId, read.toList(), bookmarked))
    }

    fun selectByReadAndBookmarked(
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
        return flowOf(db.entryQueries.selectByReadAndBookmarked(read.toList(), bookmarked))
    }

    fun selectCount(): Flow<Long> = flowOf(db.entryQueries.selectCount())

    private fun selectMaxId(): Flow<String?> = flowOf(db.entryQueries.selectMaxId())

    private fun selectMaxUpdated(): Flow<String?> = flowOf(db.entryQueries.selectMaxUpdated())

    fun selectByFtsQuery(query: String): Flow<List<SelectByQuery>> {
        return flowOf(db.entrySearchQueries.selectByQuery(query))
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByFeedId(read, feedId)
        }
    }

    suspend fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByBookmarked(read, bookmarked)
        }
    }

    suspend fun updateReadAndReadSynced(id: String, read: Boolean, readSynced: Boolean) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadAndReadSynced(id, read, readSynced)
        }
    }

    suspend fun updateBookmarkedAndBookmaredSynced(
        id: String,
        bookmarked: Boolean,
        bookmarkedSynced: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateBookmarkedAndBookmaredSynced(id, bookmarked, bookmarkedSynced)
        }
    }

    suspend fun syncAll(): Flow<SyncProgress> = kotlinx.coroutines.flow.flow {
        emit(SyncProgress(0L))

        var entriesLoaded = 0L
        emit(SyncProgress(entriesLoaded))

        api.getEntries(false).collect { batch ->
            entriesLoaded += batch.getOrThrow().size
            emit(SyncProgress(entriesLoaded))
            insertOrReplace(batch.getOrThrow())
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.IO) {
            val unsyncedEntries = db.entryQueries.selectByReadSynced(false)

            if (unsyncedEntries.isEmpty()) {
                return@withContext
            }

            val unsyncedReadEntries = unsyncedEntries.filter { it.extRead }

            if (unsyncedReadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedReadEntries.map { it.id },
                    read = true,
                )

                db.transaction {
                    unsyncedReadEntries.forEach {
                        db.entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }

            val unsyncedUnreadEntries = unsyncedEntries.filter { !it.extRead }

            if (unsyncedUnreadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedUnreadEntries.map { it.id },
                    read = false,
                )

                db.transaction {
                    unsyncedUnreadEntries.forEach {
                        db.entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() {
        withContext(Dispatchers.IO) {
            val notSyncedEntries = db.entryQueries.selectByBookmarkedSynced(false)

            if (notSyncedEntries.isEmpty()) {
                return@withContext
            }

            val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.extBookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                db.transaction {
                    notSyncedBookmarkedEntries.forEach {
                        db.entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }

            val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.extBookmarked }

            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                db.transaction {
                    notSyncedNotBookmarkedEntries.forEach {
                        db.entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
        feeds: List<Feed>,
    ): Int {
        return withContext(Dispatchers.IO) {
            val lastSyncInstant = if (lastEntriesSyncDateTime.isNotBlank()) {
                OffsetDateTime.parse(lastEntriesSyncDateTime)
            } else {
                null
            }

            val maxUpdated = db.entryQueries.selectMaxUpdated()

            val maxUpdatedInstant = if (maxUpdated != null) {
                OffsetDateTime.parse(maxUpdated)
            } else {
                null
            }

            val entries = api.getNewAndUpdatedEntries(
                lastSync = lastSyncInstant,
                maxEntryId = db.entryQueries.selectMaxId(),
                maxEntryUpdated = maxUpdatedInstant,
            ).getOrThrow()

            db.transaction {
                entries.forEach { newEntry ->
                    val feed = feeds.firstOrNull { it.id == newEntry.feedId }
                    val postProcessedEntry = newEntry.postProcess(feed)

                    val oldLinks = db.entryQueries.selectLinksById(newEntry.id)
                        ?: emptyList()

                    db.entryQueries.insertOrReplace(
                        postProcessedEntry.copy(links = oldLinks.ifEmpty { newEntry.links })
                    )
                }
            }

            entries.size
        }
    }

    private fun Entry.postProcess(feed: Feed? = null): Entry {
        var processedEntry = this

        if (contentText != null && contentText.toByteArray().size / 1024 > 250) {
            processedEntry = processedEntry.copy(contentText = "Content is too large")
        }

        feed?.extBlockedWords?.split(",")?.filter { it.isNotBlank() }?.forEach { word ->
            if (processedEntry.title.contains(word, ignoreCase = true)) {
                processedEntry = processedEntry.copy(extRead = true)
            }
        }

        return processedEntry
    }

    data class SyncProgress(val itemsSynced: Long)
}
