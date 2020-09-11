package co.appreactor.nextcloud.news.feeds

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.common.DatabaseSyncManager

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val databaseSyncManager: DatabaseSyncManager
) : ViewModel() {

    suspend fun createFeed(url: String) {
        feedsRepository.create(url)

        databaseSyncManager.sync(
            syncNewsFlags = false,
            syncFeeds = true,
            fetchNewAndUpdatedNews = true
        )
    }

    suspend fun getFeeds() = feedsRepository.all()

    suspend fun deleteFeed(id: Long) {
        feedsRepository.deleteById(id)
    }
}