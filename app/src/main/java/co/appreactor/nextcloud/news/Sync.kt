package co.appreactor.nextcloud.news

import timber.log.Timber

class Sync(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences
) {

    suspend fun performInitialSyncIfNoData() {
        newsItemsRepository.performInitialSyncIfNoData()
        newsFeedsRepository.reloadFromApiIfNoData()
        prefs.putBoolean(Preferences.INITIAL_SYNC_COMPLETED, true)
    }

    suspend fun syncNewsFlagsOnly() = sync(
        syncNewsFlags = true,
        syncFolders = false,
        syncFeeds = false,
        fetchNewAndUpdatedNews = false
    )

    suspend fun sync(
        syncNewsFlags: Boolean = true,
        syncFolders: Boolean = true,
        syncFeeds: Boolean = true,
        fetchNewAndUpdatedNews: Boolean = true
    ) {
        Timber.d("Started sync")

        if (syncNewsFlags) {
            Timber.d("Notifying the News app of read and unread articles")
            newsItemsRepository.syncUnreadFlags()

            Timber.d("Notifying the News app of starred and unstarred articles")
            newsItemsRepository.syncStarredFlags()
        }

        if (syncFolders) {
            // Step 5
            // Get new folders: GET /folders
            // TODO
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
    }
}