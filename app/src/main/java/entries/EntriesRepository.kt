package entries

import api.*
import api.GetEntriesResult
import db.Entry
import db.EntryQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.joda.time.Instant
import timber.log.Timber

class EntriesRepository(
    private val api: NewsApi,
    private val db: EntryQueries,
) {

    suspend fun selectAll() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun get(entryId: String) = withContext(Dispatchers.IO) {
        db.selectById(entryId).asFlow().mapToOneOrNull()
    }

    suspend fun selectByFeedId(feedId: String) = withContext(Dispatchers.IO) {
        db.selectByFeedId(feedId).asFlow().mapToList()
    }

    suspend fun getNotOpened() = withContext(Dispatchers.IO) {
        db.selectByOpened(false).asFlow().mapToList()
    }

    suspend fun setOpened(id: String, opened: Boolean) = withContext(Dispatchers.IO) {
        db.apply {
            transaction {
                updateOpened(opened, id)
                updateOpenedSynced(false, id)
            }
        }
    }

    suspend fun getBookmarked() = withContext(Dispatchers.IO) {
        db.selectByBookmarked(true).asFlow().mapToList()
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        db.apply {
            transaction {
                updateBookmarked(bookmarked, id)
                updateBookmarkedSynced(false, id)
            }
        }
    }

    suspend fun getUnreadCount(feedId: String) = withContext(Dispatchers.IO) {
        db.selectUnreadCount(feedId).asFlow().mapToOne()
    }

    private suspend fun getMaxUpdated() = withContext(Dispatchers.IO) {
        db.selectMaxUpdaded().executeAsOneOrNull()?.MAX
    }

    suspend fun search(query: String) = withContext(Dispatchers.IO) {
        db.selectByQuery(query).asFlow().mapToList()
    }

    fun deleteByFeedId(feedId: String) {
        db.deleteByFeedId(feedId)
    }

    suspend fun syncAll(): Flow<SyncProgress> = flow {
        emit(SyncProgress(0L))

        withContext(Dispatchers.IO) {
            api.getAllEntries().collect { result ->
                when (result) {
                    is GetEntriesResult.Loading -> {
                        emit(SyncProgress(result.entriesLoaded))
                    }

                    is GetEntriesResult.Success -> {
                        db.transaction {
                            result.entries.forEach {
                                db.insertOrReplace(it.toSafeToInsertEntry())
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun syncOpenedEntries() = withContext(Dispatchers.IO) {
        val notSyncedEntries = db.selectByOpenedSynced(false).executeAsList()

        if (notSyncedEntries.isEmpty()) {
            return@withContext
        }

        val notSyncedOpenedEntries = notSyncedEntries.filter { it.opened }

        if (notSyncedOpenedEntries.isNotEmpty()) {
            api.markAsOpened(
                entriesIds = notSyncedOpenedEntries.map { it.id },
                opened = true,
            )

            db.transaction {
                notSyncedOpenedEntries.forEach {
                    db.updateOpenedSynced(true, it.id)
                }
            }
        }

        val notSyncedNotOpenedEntries = notSyncedEntries.filterNot { it.opened }

        if (notSyncedNotOpenedEntries.isNotEmpty()) {
            api.markAsOpened(
                entriesIds = notSyncedNotOpenedEntries.map { it.id },
                opened = false,
            )

            db.transaction {
                notSyncedNotOpenedEntries.forEach {
                    db.updateOpenedSynced(true, it.id)
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
            api.markAsBookmarked(notSyncedBookmarkedEntries, true)

            db.transaction {
                notSyncedBookmarkedEntries.forEach {
                    db.updateBookmarkedSynced(true, it.id)
                }
            }
        }

        val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.bookmarked }

        if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
            api.markAsBookmarked(notSyncedNotBookmarkedEntries, false)

            db.transaction {
                notSyncedNotBookmarkedEntries.forEach {
                    db.updateBookmarkedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(lastEntriesSyncDateTime: String) = withContext(Dispatchers.IO) {
        val threshold = getMaxUpdated() ?: lastEntriesSyncDateTime

        if (threshold.isBlank()) {
            throw Exception("Can not find any reference dates")
        }

        val since = Instant.parse(threshold)
        val entries = api.getNewAndUpdatedEntries(since)

        db.transaction {
            entries.forEach {
                db.insertOrReplace(it.toSafeToInsertEntry())
            }
        }
    }

    private fun Entry.toSafeToInsertEntry(): Entry {
        var safeEntry = this

        if (content.toByteArray().size / 1024 > 100) {
            Timber.d("Entry content is larger than 100 KiB ($link)")
            safeEntry = safeEntry.copy(content = "Content is too large")
        }

        return safeEntry
    }

    data class SyncProgress(val itemsSynced: Long)
}