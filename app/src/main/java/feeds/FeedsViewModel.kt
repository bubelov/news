package feeds

import androidx.lifecycle.ViewModel
import common.NewsApiSync
import db.Feed
import entries.EntriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import opml.OpmlElement
import timber.log.Timber

class FeedsViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    val state = MutableStateFlow<State>(State.Inactive)

    suspend fun onViewReady() {
        if (state.value == State.Inactive) {
            state.value = State.LoadingFeeds
        }

        state.value = State.LoadedFeeds(
            feeds = feedsRepository.selectAll().map { it.toItem() }
        )
    }

    suspend fun createFeed(url: String) {
        feedsRepository.insertByUrl(url)

        newsApiSync.sync(
            syncEntriesFlags = false,
            syncFeeds = true,
            syncNewAndUpdatedEntries = false,
        )
    }

    suspend fun renameFeed(feedId: String, newTitle: String) {
        feedsRepository.updateTitle(feedId, newTitle)
    }

    suspend fun deleteFeed(feedId: String) {
        feedsRepository.deleteById(feedId)
        entriesRepository.deleteByFeedId(feedId)
    }

    suspend fun getAllFeeds() = feedsRepository.selectAll()

    suspend fun importFeeds(feeds: List<OpmlElement>) {
        var added = 0
        var exists = 0
        var failed = 0
        val errors = mutableListOf<String>()

        val progressFlow = MutableStateFlow(
            FeedImportProgress(
                imported = 0,
                total = feeds.size,
            )
        )

        state.value = State.ImportingFeeds(progressFlow)

        val cachedFeeds = feedsRepository.selectAll()

        feeds.forEach { opml ->
            if (cachedFeeds.any { it.selfLink == opml.xmlUrl }) {
                exists++
                return@forEach
            }

            runCatching {
                feedsRepository.insertByUrl(opml.xmlUrl.replace("http://", "https://"))
            }.onSuccess {
                added++
            }.onFailure {
                errors += "Failed to import feed ${opml.xmlUrl}\nReason: ${it.message}"
                Timber.e(it)
                failed++
            }

            progressFlow.value = FeedImportProgress(
                imported = added + exists + failed,
                total = feeds.size,
            )
        }

        state.value = State.DisplayingImportResult(
            FeedImportResult(
                added = added,
                exists = exists,
                failed = failed,
                errors = errors,
            )
        )
    }

    private suspend fun Feed.toItem(): FeedsAdapterItem = FeedsAdapterItem(
        id = id,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
        unreadCount = entriesRepository.getUnreadCount(id),
    )

    sealed class State {
        object Inactive : State()
        object LoadingFeeds : State()
        data class LoadedFeeds(val feeds: List<FeedsAdapterItem>) : State()
        data class ImportingFeeds(val progress: MutableStateFlow<FeedImportProgress>) : State()
        data class DisplayingImportResult(val result: FeedImportResult) : State()
    }

    data class FeedImportProgress(
        val imported: Int,
        val total: Int,
    )

    data class FeedImportResult(
        val added: Int,
        val exists: Int,
        val failed: Int,
        val errors: List<String>,
    )
}