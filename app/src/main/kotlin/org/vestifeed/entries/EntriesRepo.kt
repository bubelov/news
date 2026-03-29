package org.vestifeed.entries

import org.vestifeed.api.Api
import org.vestifeed.db.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class EntriesRepo(
    private val api: Api,
    private val db: Database,
) {
    fun syncAll(): Flow<SyncProgress> = kotlinx.coroutines.flow.flow {
        emit(SyncProgress(0L))

        var entriesLoaded = 0L
        emit(SyncProgress(entriesLoaded))

        api.getEntries(false).collect { batch ->
            entriesLoaded += batch.getOrThrow().size
            emit(SyncProgress(entriesLoaded))
            db.transaction {
                db.entry.insertOrReplace(batch.getOrThrow())
            }
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.IO) {
            val unsyncedEntries = db.entry.selectByReadSynced(false)

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
                        db.entry.updateReadSynced(true, it.id)
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
                        db.entry.updateReadSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() {
        withContext(Dispatchers.IO) {
            val notSyncedEntries = db.entry.selectByBookmarkedSynced(false)

            if (notSyncedEntries.isEmpty()) {
                return@withContext
            }

            val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.extBookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                db.transaction {
                    notSyncedBookmarkedEntries.forEach {
                        db.entry.updateBookmarkedSynced(true, it.id)
                    }
                }
            }

            val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.extBookmarked }

            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                db.transaction {
                    notSyncedNotBookmarkedEntries.forEach {
                        db.entry.updateBookmarkedSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
    ): Int {
        return withContext(Dispatchers.IO) {
            val lastSyncInstant = if (lastEntriesSyncDateTime.isNotBlank()) {
                OffsetDateTime.parse(lastEntriesSyncDateTime)
            } else {
                null
            }

            val maxUpdated = db.entry.selectMaxUpdated()

            val maxUpdatedInstant = if (maxUpdated != null) {
                OffsetDateTime.parse(maxUpdated)
            } else {
                null
            }

            val entries = api.getNewAndUpdatedEntries(
                lastSync = lastSyncInstant,
                maxEntryId = db.entry.selectMaxId(),
                maxEntryUpdated = maxUpdatedInstant,
            ).getOrThrow()

            db.transaction {
                db.entry.insertOrReplace(entries)
            }

            entries.size
        }
    }

    data class SyncProgress(val itemsSynced: Long)
}
