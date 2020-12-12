package common

import feeds.FeedsRepository
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NewsApiSync(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val prefs: Preferences,
    private val connectivityProbe: ConnectivityProbe,
) {

    val syncMessage = MutableStateFlow("")

    private val mutex = Mutex()

    suspend fun performInitialSync() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (prefs.initialSyncCompleted().first()) {
                    return@withLock
                }

                runCatching {
                    val feedsSync = async { feedsRepository.sync() }

                    val entriesSync = async {
                        entriesRepository.syncUnopenedAndBookmarked().collect { progress ->
                            var message = "Fetching news..."

                            if (progress.itemsSynced > 0) {
                                message += " Got ${progress.itemsSynced} items so far..."
                            }

                            syncMessage.value = message
                        }
                    }

                    feedsSync.await()
                    entriesSync.await()
                }.onSuccess {
                    syncMessage.value = ""
                    prefs.setInitialSyncCompleted(true)
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
    ) {
        connectivityProbe.throwIfOffline()

        mutex.withLock {
            if (syncEntriesFlags) {
                runCatching {
                    entriesRepository.syncOpenedEntries()
                }.onFailure {
                    throw Exception("Can't sync opened news", it)
                }

                runCatching {
                    entriesRepository.syncBookmarkedEntries()
                }.onFailure {
                    throw Exception("Can't sync bookmarks", it)
                }
            }

            if (syncFeeds) {
                feedsRepository.sync()
            }

            if (syncNewAndUpdatedEntries) {
                entriesRepository.syncNewAndUpdated()
            }
        }
    }
}