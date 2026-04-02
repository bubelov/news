package org.vestifeed.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.api.Api
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.Database
import java.time.Instant
import java.time.OffsetDateTime

class Sync(
    private val scope: CoroutineScope,
    private val api: Api,
    private val db: Database,
) {
    sealed class State {
        data class Idle(val error: Throwable? = null) : State()
        object Starting : State()
        data class InitialSync(val stage: InitialSyncStage) : State()
        data class FollowUpSync(val args: Args, val stage: FollowUpSyncStage) : State()
    }

    sealed class InitialSyncStage {
        object SyncingFeeds : InitialSyncStage()
        data class SyncingEntries(val entriesSynced: Long) : InitialSyncStage()
    }

    sealed class FollowUpSyncStage {
        object SyncingFeeds : FollowUpSyncStage()
        object SyncingFlags : FollowUpSyncStage()
        object SyncingEntries : FollowUpSyncStage()
    }

    private val _state = MutableStateFlow<State>(State.Idle())
    val state = _state.asStateFlow()

    data class Args(
        val syncFeeds: Boolean = true,
        val syncFlags: Boolean = true,
        val syncEntries: Boolean = true,
    )

    fun runInBackground(args: Args = Args()) {
        scope.launch { run(args) }
    }

    suspend fun runInForeground(args: Args = Args()) {
        run(args)
    }

    private suspend fun run(args: Args = Args()) {
        // todo add request queue
        if (_state.value !is State.Idle) {
            return
        }

        _state.update { State.Starting }

        val conf = withContext(Dispatchers.IO) { db.conf.select() }

        if (conf.backend.isBlank()) {
            _state.update { State.Idle(IllegalStateException("backend is not set")) }
            return
        }

        if (conf.backend != ConfQueries.BACKEND_STANDALONE && !conf.initialSyncCompleted) {
            // make sure the database is empty
            try {
                withContext(Dispatchers.IO) {
                    db.transaction {
                        db.feed.deleteAll()
                        db.entry.deleteAll()
                    }
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }

            // feeds should be fetched first
            try {
                _state.update { State.InitialSync(InitialSyncStage.SyncingFeeds) }
                val feeds = api.getFeeds()
                db.transaction { db.feed.insertOrReplace(feeds) }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }

            // entries are fetched in batches
            try {
                var entriesFetched = 0L
                _state.update { State.InitialSync(InitialSyncStage.SyncingEntries(entriesFetched)) }

                api.getEntries(includeReadEntries = false).collect { batch ->
                    entriesFetched += batch.size
                    _state.update { State.InitialSync(InitialSyncStage.SyncingEntries(entriesFetched)) }

                    withContext(Dispatchers.IO) {
                        db.transaction {
                            db.entry.insertOrReplace(batch)
                        }
                    }
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }

            withContext(Dispatchers.IO) {
                db.conf.update {
                    it.copy(
                        initialSyncCompleted = true,
                        lastEntriesSyncDatetime = Instant.now().toString(),
                    )
                }
            }
        }

        if (args.syncFlags) {
            _state.update { State.FollowUpSync(args, FollowUpSyncStage.SyncingFlags) }

            try {
                val unsyncedEntries =
                    withContext(Dispatchers.IO) { db.entry.selectByReadSynced(false) }
                val unsyncedReadEntries = unsyncedEntries.filter { it.extRead }
                val unsyncedUnreadEntries = unsyncedEntries.filter { !it.extRead }

                if (unsyncedReadEntries.isNotEmpty()) {
                    api.markEntriesAsRead(
                        entriesIds = unsyncedReadEntries.map { it.id },
                        read = true,
                    )

                    withContext(Dispatchers.IO) {
                        db.transaction {
                            unsyncedReadEntries.forEach {
                                db.entry.updateReadSynced(true, it.id)
                            }
                        }
                    }
                }

                if (unsyncedUnreadEntries.isNotEmpty()) {
                    api.markEntriesAsRead(
                        entriesIds = unsyncedUnreadEntries.map { it.id },
                        read = false,
                    )

                    withContext(Dispatchers.IO) {
                        db.transaction {
                            unsyncedUnreadEntries.forEach {
                                db.entry.updateReadSynced(true, it.id)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }

            try {
                val notSyncedEntries =
                    withContext(Dispatchers.IO) { db.entry.selectByBookmarkedSynced(false) }
                val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.extBookmarked }
                val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.extBookmarked }

                if (notSyncedBookmarkedEntries.isNotEmpty()) {
                    api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                    withContext(Dispatchers.IO) {
                        db.transaction {
                            notSyncedBookmarkedEntries.forEach {
                                db.entry.updateBookmarkedSynced(true, it.id)
                            }
                        }
                    }
                }

                if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                    api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                    withContext(Dispatchers.IO) {
                        db.transaction {
                            notSyncedNotBookmarkedEntries.forEach {
                                db.entry.updateBookmarkedSynced(true, it.id)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }
        }

        if (args.syncFeeds) {
            _state.update {
                State.FollowUpSync(args, FollowUpSyncStage.SyncingFeeds)
            }

            try {
                val newSnapshot = api.getFeeds()
                val cachedSnapshot = withContext(Dispatchers.IO) { db.feed.selectAll() }

                val insertQueue = withContext(Dispatchers.IO) {
                    newSnapshot.map {
                        val cachedFeed = cachedSnapshot.find { cached -> cached.id == it.id }

                        if (cachedFeed == null) {
                            it
                        } else {
                            it.copy(
                                extOpenEntriesInBrowser = cachedFeed.extOpenEntriesInBrowser,
                                extBlockedWords = cachedFeed.extBlockedWords,
                                extShowPreviewImages = cachedFeed.extShowPreviewImages,
                            )
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    db.transaction {
                        db.feed.deleteAll()
                        db.feed.insertOrReplace(insertQueue)
                    }
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }
        }

        if (args.syncEntries) {
            _state.update {
                State.FollowUpSync(args, FollowUpSyncStage.SyncingEntries)
            }

            try {
                val freshConf = withContext(Dispatchers.IO) { db.conf.select() }

                val lastSyncInstant = if (freshConf.lastEntriesSyncDatetime.isNotBlank()) {
                    OffsetDateTime.parse(freshConf.lastEntriesSyncDatetime)
                } else {
                    null
                }

                val maxUpdated = withContext(Dispatchers.IO) { db.entry.selectMaxUpdated() }

                val maxUpdatedInstant = if (maxUpdated != null) {
                    OffsetDateTime.parse(maxUpdated)
                } else {
                    null
                }

                val entries = api.getNewAndUpdatedEntries(
                    lastSync = lastSyncInstant,
                    maxEntryId = db.entry.selectMaxId(),
                    maxEntryUpdated = maxUpdatedInstant,
                )

                withContext(Dispatchers.IO) {
                    db.transaction {
                        db.entry.insertOrReplace(entries)
                    }
                }

                db.conf.update {
                    it.copy(
                        lastEntriesSyncDatetime = Instant.now().toString()
                    )
                }
            } catch (e: Throwable) {
                _state.update { State.Idle(e) }
                return
            }
        }

        _state.update { State.Idle() }
    }
}