package feeds

import androidx.lifecycle.ViewModel
import common.NewsApiSync
import entries.EntriesRepository
import kotlinx.coroutines.flow.map

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

    suspend fun getFeeds() = feedsRepository.getAll().map { feeds ->
        feeds.map { feed ->
            FeedsAdapterItem(
                id = feed.id,
                title = feed.title,
                selfLink = feed.selfLink,
                alternateLink = feed.alternateLink,
                unreadCount = entriesRepository.getUnreadCount(feed.id),
            )
        }
    }

    suspend fun renameFeed(feedId: String, newTitle: String) {
        feedsRepository.updateTitle(feedId, newTitle)
    }

    suspend fun deleteFeed(feedId: String) {
        feedsRepository.delete(feedId)
        entriesRepository.deleteByFeedId(feedId)
    }
}