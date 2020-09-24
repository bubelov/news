package co.appreactor.news.common

import co.appreactor.news.feeds.FeedsRepository
import co.appreactor.news.entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    private val mutex = Mutex()

    suspend fun performInitialSync() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (prefs.initialSyncCompleted().first()) {
                    return@withLock
                }

                val feedsSync = async { feedsRepository.sync() }
                val entriesSync = async { entriesRepository.syncNotViewedAndBookmarked() }

                feedsSync.await()
                entriesSync.await()

                prefs.setInitialSyncCompleted(true)
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
                entriesRepository.syncViewedFlags()
                entriesRepository.syncBookmarkedFlags()
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