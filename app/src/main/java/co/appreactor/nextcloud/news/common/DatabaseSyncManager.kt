package co.appreactor.nextcloud.news.common

import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

class DatabaseSyncManager(
    private val feedItemsRepository: FeedItemsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences
) {

    suspend fun performInitialSyncIfNoData() {
        runCatching {
            val syncTime = measureTimeMillis {
                withContext(Dispatchers.IO) {
                    val feedsSync = async { feedsRepository.reloadFromApiIfNoData() }
                    val feedItemsSync = async { feedItemsRepository.performInitialSyncIfNoData() }

                    feedsSync.await()
                    feedItemsSync.await()
                }
            }

            Timber.d("Initial sync time: $syncTime")
            prefs.putBoolean(Preferences.INITIAL_SYNC_COMPLETED, true)
        }.onFailure {
            Timber.e(it)
            throw Exception("Cannot fetch data from Nextcloud")
        }
    }

    suspend fun syncNewsFlagsOnly() = sync(
        syncNewsFlags = true,
        syncFeeds = false,
        fetchNewAndUpdatedNews = false
    )

    suspend fun sync(
        syncNewsFlags: Boolean = true,
        syncFeeds: Boolean = true,
        fetchNewAndUpdatedNews: Boolean = true
    ) {
        runCatching {
            Timber.d("Started sync")

            if (syncNewsFlags) {
                Timber.d("Notifying the News app of read and unread articles")
                feedItemsRepository.syncUnreadFlags()

                Timber.d("Notifying the News app of starred and unstarred articles")
                feedItemsRepository.syncStarredFlags()
            }

            if (syncFeeds) {
                Timber.d("Syncing feeds")
                feedsRepository.reloadFromApi()
            }

            if (fetchNewAndUpdatedNews) {
                Timber.d("Syncing new and updated news items")
                feedItemsRepository.fetchNewAndUpdatedItems()
            }

            Timber.d("Finished sync")
        }.getOrElse {
            Timber.e(it)
            throw it
        }
    }
}