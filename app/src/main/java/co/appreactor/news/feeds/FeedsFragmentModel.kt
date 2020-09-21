package co.appreactor.news.feeds

import androidx.lifecycle.ViewModel
import co.appreactor.news.common.NewsApiSync

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    suspend fun createFeed(url: String) {
        feedsRepository.add(url)

        newsApiSync.sync(
            syncEntriesFlags = false,
            syncFeeds = true,
            syncNewAndUpdatedEntries = false
        )
    }

    suspend fun getFeeds() = feedsRepository.getAll()

    suspend fun renameFeed(feedId: String, newTitle: String) {
        feedsRepository.rename(feedId, newTitle)
    }

    suspend fun deleteFeed(id: String) {
        feedsRepository.delete(id)
    }
}