package co.appreactor.nextcloud.news.feeds

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.common.NewsApiSync

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    suspend fun createFeed(url: String) {
        feedsRepository.create(url)

        newsApiSync.sync(
            syncFeedItemsFlags = false,
            syncFeeds = true,
            fetchNewAndUpdatedFeedItems = true
        )
    }

    suspend fun getFeeds() = feedsRepository.all()

    suspend fun deleteFeed(id: Long) {
        feedsRepository.deleteById(id)
    }
}