package org.vestifeed.sync

import org.vestifeed.conf.ConfRepo
import org.vestifeed.entries.EntriesRepo
import org.vestifeed.feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant

class Sync(
    private val confRepo: ConfRepo,
    private val feedsRepo: FeedsRepo,
    private val entriesRepo: EntriesRepo,
) {

    sealed class State {
        object Idle : State()
        data class InitialSync(val message: String = "") : State()
        data class FollowUpSync(val args: Args) : State()
    }

    data class Args(
        val syncFeeds: Boolean = true,
        val syncFlags: Boolean = true,
        val syncEntries: Boolean = true,
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    suspend fun run(args: Args = Args()): SyncResult {
        if (_state.value != State.Idle) {
            return SyncResult.Failure(Exception("Already syncing"))
        }

        val conf = confRepo.conf.value

        if (!conf.initialSyncCompleted) {
            _state.update { State.InitialSync() }

            runCatching {
                feedsRepo.sync()
            }.onFailure {
                _state.update { State.Idle }
                return SyncResult.Failure(Exception("Failed to org.vestifeed.sync org.vestifeed.feeds", it))
            }

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
                return SyncResult.Failure(Exception("Failed to org.vestifeed.sync org.vestifeed.entries", it))
            }

            confRepo.update {
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
                    return SyncResult.Failure(Exception("Failed to org.vestifeed.sync read news", it))
                }

                runCatching {
                    entriesRepo.syncBookmarkedEntries()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to org.vestifeed.sync bookmarks", it))
                }
            }

            if (args.syncFeeds) {
                runCatching {
                    feedsRepo.sync()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to org.vestifeed.sync org.vestifeed.feeds", it))
                }
            }

            return if (args.syncEntries) {
                runCatching {
                    val newAndUpdatedEntries = entriesRepo.syncNewAndUpdated(
                        lastEntriesSyncDateTime = confRepo.conf.value.lastEntriesSyncDatetime,
                        feeds = feedsRepo.selectAll().first(),
                    )

                    confRepo.update { it.copy(lastEntriesSyncDatetime = Instant.now().toString()) }
                    _state.update { State.Idle }
                    SyncResult.Success(newAndUpdatedEntries)
                }.getOrElse {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to org.vestifeed.sync new and updated org.vestifeed.entries", it))
                }
            } else {
                _state.update { State.Idle }
                SyncResult.Success(0)
            }
        }
    }
}