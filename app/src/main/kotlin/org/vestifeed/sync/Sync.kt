package org.vestifeed.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import org.vestifeed.entries.EntriesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.vestifeed.api.Api
import org.vestifeed.db.Database
import java.time.Instant

class Sync(
    private val api: Api,
    private val db: Database,
    private val entriesRepo: EntriesRepo,
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

    suspend fun run(args: Args = Args()): SyncResult {
        if (_state.value != State.Idle) {
            return SyncResult.Failure(Exception("Already syncing"))
        }

        val conf = db.conf.select()

        if (!conf.initialSyncCompleted) {
            Log.d("sync", "launching initial sync")
            _state.update { State.InitialSync() }

            try {
                Log.d("sync", "syncing feeds")
                syncFeeds()
                Log.d("sync", "done syncing feeds")
            } catch (e: Throwable) {
                Log.d("sync", "error syncing feeds")
                _state.update { State.Idle }
                return SyncResult.Failure(
                    Exception(
                        "failed to sync feeds",
                        e,
                    )
                )
            }

            Log.d("sync", "syncing entries")

            runCatching {
                entriesRepo.syncAll().collect { progress ->
                    var message = "Fetching news"

                    if (progress.itemsSynced > 0) {
                        message += "\n Got ${progress.itemsSynced} items so far"
                    }

                    _state.update { State.InitialSync(message) }
                }
            }.onFailure {
                _state.update { State.Idle }
                return SyncResult.Failure(
                    Exception(
                        "Failed to org.vestifeed.sync org.vestifeed.entries",
                        it
                    )
                )
            }

            db.conf.update {
                it.copy(
                    initialSyncCompleted = true,
                    lastEntriesSyncDatetime = Instant.now().toString(),
                )
            }

            _state.update { State.Idle }
            return SyncResult.Success(0)
        } else {
            _state.update { State.FollowUpSync(args) }

            if (args.syncFlags) {
                runCatching {
                    entriesRepo.syncReadEntries()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(
                        Exception(
                            "Failed to org.vestifeed.sync read news",
                            it
                        )
                    )
                }

                runCatching {
                    entriesRepo.syncBookmarkedEntries()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(
                        Exception(
                            "Failed to org.vestifeed.sync bookmarks",
                            it
                        )
                    )
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
                    return SyncResult.Failure(
                        Exception(
                            "Failed to sync feeds",
                            it,
                        )
                    )
                }
            }

            return if (args.syncEntries) {
                runCatching {
                    val newAndUpdatedEntries = entriesRepo.syncNewAndUpdated(
                        lastEntriesSyncDateTime = db.conf.select().lastEntriesSyncDatetime,
                    )

                    db.conf.update {
                        it.copy(
                            lastEntriesSyncDatetime = Instant.now().toString()
                        )
                    }
                    _state.update { State.Idle }
                    SyncResult.Success(newAndUpdatedEntries)
                }.getOrElse {
                    _state.update { State.Idle }
                    return SyncResult.Failure(
                        Exception(
                            "Failed to org.vestifeed.sync new and updated org.vestifeed.entries",
                            it
                        )
                    )
                }
            } else {
                _state.update { State.Idle }
                SyncResult.Success(0)
            }
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
}