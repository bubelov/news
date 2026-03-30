package org.vestifeed.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.vestifeed.api.Api
import org.vestifeed.db.Database
import java.time.Instant
import java.time.OffsetDateTime

class Sync(
    private val api: Api,
    private val db: Database,
) {
    sealed class State {
        object Idle : State()
        data class InitialSync(val message: String = "") : State()
        data class FollowUpSync(val args: Args) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    data class Args(
        val syncFeeds: Boolean = true,
        val syncFlags: Boolean = true,
        val syncEntries: Boolean = true,
    )

    suspend fun maybeInitialSync() {
        Log.d("sync", "maybeInitialSync")

        if (_state.value != State.Idle) {
            Log.d("sync", "already syncing, maybe no")
            return
        }

        val conf = withContext(Dispatchers.IO) {
            db.conf.select()
        }

        if (conf.initialSyncCompleted) {
            Log.d("sync", "initial sync was previously completed, maybe no")
            return
        }

        Log.d("sync", "launching initial sync")
        _state.update { State.InitialSync() }

        try {
            Log.d("sync", "syncing feeds")
            _state.update { State.InitialSync("syncing feeds") }
            delay(1_000)
            syncFeeds()
            Log.d("sync", "done syncing feeds")
        } catch (e: Throwable) {
            Log.e("sync", "error syncing feeds", e)
            _state.update { State.Idle }
            return
        }

        Log.d("sync", "syncing entries")
        _state.update { State.InitialSync("syncing entries") }

        try {
            syncAllEntries().collect { progress ->
                delay(500)
                var message = "syncing entries"

                if (progress.itemsSynced > 0) {
                    message += " (${progress.itemsSynced})"
                }

                Log.d("sync", message)
                _state.update { State.InitialSync(message) }
            }
        } catch (_: Throwable) {
            _state.update { State.Idle }
            return
        }

        db.conf.update {
            it.copy(
                initialSyncCompleted = true,
                lastEntriesSyncDatetime = Instant.now().toString(),
            )
        }

        _state.update { State.Idle }
    }

    suspend fun run(args: Args = Args()) {
        if (_state.value != State.Idle) {
            return
        }

        maybeInitialSync()

        _state.update { State.FollowUpSync(args) }

        if (args.syncFlags) {
            runCatching {
                syncReadEntries()
            }.onFailure {
                _state.update { State.Idle }
                return
            }

            runCatching {
                syncBookmarkedEntries()
            }.onFailure {
                _state.update { State.Idle }
                return
            }
        }

        if (args.syncFeeds) {
            Log.d("sync", "syncing feeds")
            runCatching {
                syncFeeds()
                Log.d("sync", "syncing feeds success")
            }.onFailure {
                Log.d("sync", "syncing feeds failure")
                _state.update { State.Idle }
                return
            }
        }

        return if (args.syncEntries) {
            Log.d("sync", "syncing entries")
            runCatching {
                syncNewAndUpdatedEntries(
                    lastEntriesSyncDateTime = db.conf.select().lastEntriesSyncDatetime,
                )

                db.conf.update {
                    it.copy(
                        lastEntriesSyncDatetime = Instant.now().toString()
                    )
                }
                Log.d("sync", "syncing entries success")
                _state.update { State.Idle }
                Log.d("sync", "syncing entries success returning result")
                return
            }.getOrElse {
                _state.update { State.Idle }
                return
            }
        } else {
            _state.update { State.Idle }
        }
    }

    private suspend fun syncFeeds() {
        withContext(Dispatchers.IO) {
            Log.d("sync", "requesting feeds from api")
            val newFeeds = api.getFeeds().getOrThrow().sortedBy { it.id }
            Log.d("sync", "got ${newFeeds.size} feeds")
            Log.d("sync", "getting cached feeds")
            val cachedFeeds = db.feed.selectAll()
            Log.d("sync", "got ${cachedFeeds.size} cached feeds")
            Log.d("sync", "preparing write transaction")
            db.transaction {
                db.feed.deleteAll()

                newFeeds.forEach { feed ->
                    val cachedFeed = cachedFeeds.find { it.id == feed.id }

                    db.feed.insertOrReplace(
                        feed.copy(
                            extOpenEntriesInBrowser = cachedFeed?.extOpenEntriesInBrowser ?: false,
                            extBlockedWords = cachedFeed?.extBlockedWords ?: "",
                            extShowPreviewImages = cachedFeed?.extShowPreviewImages,
                        )
                    )
                }
            }
            Log.d("sync", "finished write transaction")
        }
        Log.d("sync", "returning")
    }

    fun syncAllEntries(): Flow<SyncProgress> = kotlinx.coroutines.flow.flow {
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

    suspend fun syncNewAndUpdatedEntries(
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