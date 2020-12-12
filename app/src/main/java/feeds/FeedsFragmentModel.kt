package feeds

import androidx.lifecycle.ViewModel
import common.NewsApiSync
import entries.EntriesRepository

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
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
        feedsRepository.updateTitle(feedId, newTitle)
    }

    suspend fun deleteFeed(feedId: String) {
        feedsRepository.delete(feedId)
        entriesRepository.deleteByFeedId(feedId)
    }
}