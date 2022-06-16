package sync

import common.ConfRepository
import entries.EntriesRepository
import feeds.FeedsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import java.time.Instant

@Single
class NewsApiSync(
    private val confRepo: ConfRepository,
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
) {

    sealed class State {
        object Idle : State()
        data class InitialSync(val message: String = "") : State()
        data class FollowUpSync(val args: SyncArgs) : State()
    }

    data class SyncArgs(
        val syncFeeds: Boolean = true,
        val syncFlags: Boolean = true,
        val syncEntries: Boolean = true,
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    suspend fun sync(args: SyncArgs = SyncArgs()): SyncResult {
        if (_state.value != State.Idle) {
            return SyncResult.Failure(Exception("Already syncing"))
        }

        val conf = confRepo.load().first()

        if (!conf.initialSyncCompleted) {
            _state.update { State.InitialSync() }

            runCatching {
                feedsRepo.sync()
            }.onFailure {
                _state.update { State.Idle }
                return SyncResult.Failure(Exception("Failed to sync feeds", it))
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
                return SyncResult.Failure(Exception("Failed to sync entries", it))
            }

            confRepo.save {
                it.copy(
                    initialSyncCompleted = true,
                    lastEntriesSyncDateTime = Instant.now().toString(),
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
                    return SyncResult.Failure(Exception("Failed to sync read news", it))
                }

                runCatching {
                    entriesRepo.syncBookmarkedEntries()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to sync bookmarks", it))
                }
            }

            if (args.syncFeeds) {
                runCatching {
                    feedsRepo.sync()
                }.onFailure {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to sync feeds", it))
                }
            }

            return if (args.syncEntries) {
                runCatching {
                    val newAndUpdatedEntries = entriesRepo.syncNewAndUpdated(
                        lastEntriesSyncDateTime = confRepo.load().first().lastEntriesSyncDateTime,
                        feeds = feedsRepo.selectAll().first(),
                    )

                    confRepo.save { it.copy(lastEntriesSyncDateTime = Instant.now().toString()) }
                    _state.update { State.Idle }
                    SyncResult.Success(newAndUpdatedEntries)
                }.getOrElse {
                    _state.update { State.Idle }
                    return SyncResult.Failure(Exception("Failed to sync new and updated entries", it))
                }
            } else {
                _state.update { State.Idle }
                SyncResult.Success(0)
            }
        }
    }
}