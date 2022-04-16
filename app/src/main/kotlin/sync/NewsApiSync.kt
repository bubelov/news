package sync

import common.NetworkMonitor
import common.ConfRepository
import feeds.FeedsRepository
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

class NewsApiSync(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val confRepo: ConfRepository,
    private val networkMonitor: NetworkMonitor,
) {

    val syncMessage = MutableStateFlow("")

    private val mutex = Mutex()

    suspend fun performInitialSync() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (confRepo.select().first().initialSyncCompleted) {
                    return@withLock
                }

                runCatching {
                    val feedsSync = async { feedsRepository.sync() }

                    val entriesSync = async {
                        entriesRepository.syncAll().collect { progress ->
                            var message = "Fetching news"

                            if (progress.itemsSynced > 0) {
                                message += "\n Got ${progress.itemsSynced} items so far"
                            }

                            syncMessage.value = message
                        }
                    }

                    feedsSync.await()
                    entriesSync.await()

                    confRepo.insert(
                        confRepo.select().first()
                            .copy(lastEntriesSyncDateTime = Instant.now().toString())
                    )
                }.onSuccess {
                    syncMessage.value = ""
                    confRepo.insert(confRepo.select().first().copy(initialSyncCompleted = true))
                }.onFailure {
                    syncMessage.value = ""
                    throw it
                }
            }
        }
    }

    suspend fun syncEntriesFlags() = sync(
        syncFeeds = false,
        syncEntriesFlags = true,
        syncNewAndUpdatedEntries = false,
    )

    suspend fun sync(
        syncFeeds: Boolean = true,
        syncEntriesFlags: Boolean = true,
        syncNewAndUpdatedEntries: Boolean = true,
    ): SyncResult {
        if (!networkMonitor.online) {
            return SyncResult.Err(Exception("Device is offline"))
        }

        mutex.withLock {
            if (syncEntriesFlags) {
                runCatching {
                    entriesRepository.syncReadEntries()
                }.onFailure {
                    return SyncResult.Err(Exception("Can't sync opened news", it))
                }

                runCatching {
                    entriesRepository.syncBookmarkedEntries()
                }.onFailure {
                    return SyncResult.Err(Exception("Can't sync bookmarks", it))
                }
            }

            if (syncFeeds) {
                runCatching {
                    feedsRepository.sync()
                }.onFailure {
                    return SyncResult.Err(Exception("Can't sync feeds", it))
                }
            }

            if (syncNewAndUpdatedEntries) {
                runCatching {
                    val newAndUpdatedEntries = entriesRepository.syncNewAndUpdated(
                        lastEntriesSyncDateTime = confRepo.select().first().lastEntriesSyncDateTime,
                        feeds = feedsRepository.selectAll(),
                    )

                    confRepo.insert(
                        confRepo.select().first()
                            .copy(lastEntriesSyncDateTime = Instant.now().toString())
                    )

                    return SyncResult.Ok(newAndUpdatedEntries)
                }.onFailure {
                    return SyncResult.Err(Exception("Can't sync new and updated entries", it))
                }
            }
        }

        return SyncResult.Ok(0)
    }
}