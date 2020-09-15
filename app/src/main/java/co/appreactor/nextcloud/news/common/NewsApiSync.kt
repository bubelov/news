package co.appreactor.nextcloud.news.common

import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NewsApiSync(
    private val feedItemsRepository: FeedItemsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences,
) {

    private val mutex = Mutex()

    suspend fun performInitialSyncIfNotDone() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (prefs.initialSyncCompleted().first()) {
                    return@withLock
                }

                val feedsSync = async { feedsRepository.syncFeeds() }
                val feedItemsSync = async { feedItemsRepository.syncUnreadAndStarredFeedItems() }

                feedsSync.await()
                feedItemsSync.await()

                prefs.setInitialSyncCompleted(true)
            }
        }
    }

    suspend fun syncFeedItemsFlags() = sync(
        syncFeeds = false,
        syncFeedItemsFlags = true,
        fetchNewAndUpdatedFeedItems = false,
    )

    suspend fun sync(
        syncFeeds: Boolean = true,
        syncFeedItemsFlags: Boolean = true,
        fetchNewAndUpdatedFeedItems: Boolean = true,
    ) {
        mutex.withLock {
            if (syncFeedItemsFlags) {
                feedItemsRepository.syncUnreadFlags()
                feedItemsRepository.syncStarredFlags()
            }

            if (syncFeeds) {
                feedsRepository.syncFeeds()
            }

            if (fetchNewAndUpdatedFeedItems) {
                feedItemsRepository.fetchNewAndUpdatedItems()
            }
        }
    }
}