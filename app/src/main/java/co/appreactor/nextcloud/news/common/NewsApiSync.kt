package co.appreactor.nextcloud.news.common

import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

class NewsApiSync(
    private val feedItemsRepository: FeedItemsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences,
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

            prefs.putBoolean(Preferences.INITIAL_SYNC_COMPLETED, true)
        }.onFailure {
            Timber.e(it)
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
            if (syncNewsFlags) {
                feedItemsRepository.syncUnreadFlags()
                feedItemsRepository.syncStarredFlags()
            }

            if (syncFeeds) {
                feedsRepository.reloadFromApi()
            }

            if (fetchNewAndUpdatedNews) {
                feedItemsRepository.fetchNewAndUpdatedItems()
            }
        }.onFailure {
            Timber.e(it)
            throw it
        }
    }
}