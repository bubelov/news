package feeds

import androidx.lifecycle.ViewModel
import common.NewsApiSync
import common.Result
import db.Feed
import entries.EntriesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    val items = MutableStateFlow<Result<List<FeedsAdapterItem>>>(Result.Inactive)

    suspend fun onViewReady() {
        if (items.value == Result.Inactive) {
            items.value = Result.Progress
        }

        runCatching {
            feedsRepository.getAll().collect {
                items.value = withContext(Dispatchers.IO) {
                    Result.Success(it.map { it.toItem() })
                }
            }
        }.onFailure {
            if (it !is CancellationException) {
                items.value = Result.Failure(it)
            }
        }
    }

    suspend fun createFeed(url: String) {
        feedsRepository.add(url)

        newsApiSync.sync(
            syncEntriesFlags = false,
            syncFeeds = true,
            syncNewAndUpdatedEntries = false
        )
    }

    suspend fun renameFeed(feedId: String, newTitle: String) {
        feedsRepository.updateTitle(feedId, newTitle)
    }

    suspend fun deleteFeed(feedId: String) {
        feedsRepository.delete(feedId)
        entriesRepository.deleteByFeedId(feedId)
    }

    private suspend fun Feed.toItem(): FeedsAdapterItem = FeedsAdapterItem(
        id = id,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
        unreadCount = entriesRepository.getUnreadCount(id),
    )
}