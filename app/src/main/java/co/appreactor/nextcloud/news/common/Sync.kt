package co.appreactor.nextcloud.news.common

import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

class Sync(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences
) {

    suspend fun performInitialSyncIfNoData() {
        runCatching {
            val syncTime = measureTimeMillis {
                withContext(Dispatchers.IO) {
                    val newsSync = async { newsItemsRepository.performInitialSyncIfNoData() }
                    val feedsSync = async { newsFeedsRepository.reloadFromApiIfNoData() }

                    newsSync.await()
                    feedsSync.await()
                }
            }

            Timber.d("Initial sync time: $syncTime")
            prefs.putBoolean(Preferences.INITIAL_SYNC_COMPLETED, true)
        }.apply {
            if (isFailure) {
                throw Exception("Cannot fetch data from Nextcloud")
            }
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
                newsItemsRepository.syncUnreadFlags()

                Timber.d("Notifying the News app of starred and unstarred articles")
                newsItemsRepository.syncStarredFlags()
            }

            if (syncFeeds) {
                Timber.d("Syncing feeds")
                newsFeedsRepository.reloadFromApi()
            }

            if (fetchNewAndUpdatedNews) {
                Timber.d("Syncing new and updated news items")
                newsItemsRepository.fetchNewAndUpdatedItems()
            }

            Timber.d("Finished sync")
        }.apply {
            if (isFailure) {
                Timber.e(exceptionOrNull())
                throw Exception("Cannot fetch data from Nextcloud")
            }
        }
    }
}