package feeds

import android.app.Application
import androidx.lifecycle.ViewModel
import co.appreactor.news.R
import db.Feed
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import opml.exportOpml
import opml.importOpml
import timber.log.Timber
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class FeedsViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val app: Application,
) : ViewModel() {

    val state = MutableStateFlow<State>(State.Inactive)

    suspend fun loadFeeds() {
        state.value = State.Loading

        state.value = State.ShowingFeeds(
            feeds = feedsRepository.selectAll().map { it.toItem() },
        )
    }

    suspend fun createFeed(url: String) = changeState {
        value = State.Loading

        val fullUrl = if (url.startsWith("http")) url else "https://$url"

        try {
            URL(fullUrl).toURI()
        } catch (e: Exception) {
            throw Exception(app.getString(R.string.invalid_url_s, fullUrl), e)
        }

        feedsRepository.insertByFeedUrl(fullUrl)
        loadFeeds()
    }

    suspend fun renameFeed(feedId: String, newTitle: String) = changeState {
        value = State.Loading
        feedsRepository.updateTitle(feedId, newTitle)
        loadFeeds()
    }

    suspend fun deleteFeed(feedId: String) = changeState {
        value = State.Loading
        feedsRepository.deleteById(feedId)
        entriesRepository.deleteByFeedId(feedId)
        loadFeeds()
    }

    suspend fun getFeedsOpml(): ByteArray {
        return exportOpml(feedsRepository.selectAll()).toByteArray()
    }

    suspend fun importFeeds(opmlDocument: String) = changeState {
        value = State.Loading

        val feeds = runCatching {
            importOpml(opmlDocument)
        }.getOrElse {
            throw Exception("Can't parse OPML file: ${it.message}", it)
        }

        val added = AtomicInteger()
        val exists = AtomicInteger()
        val failed = AtomicInteger()
        val errors = mutableListOf<String>()

        val progressFlow = MutableStateFlow(
            FeedImportProgress(
                imported = 0,
                total = feeds.size,
            )
        )

        value = State.ImportingFeeds(progressFlow)

        val cachedFeeds = feedsRepository.selectAll()

        withContext(Dispatchers.IO) {
            feeds.chunked(10).forEach { chunk ->
                chunk.map { outline ->
                    async {
                        val cachedFeed = cachedFeeds.firstOrNull { it.selfLink == outline.xmlUrl }

                        if (cachedFeed != null) {
                            feedsRepository.insertOrReplace(
                                cachedFeed.copy(
                                    openEntriesInBrowser = outline.openEntriesInBrowser,
                                    blockedWords = outline.blockedWords,
                                )
                            )

                            exists.incrementAndGet()
                            return@async
                        }

                        runCatching {
                            feedsRepository.insertByFeedUrl(
                                url = outline.xmlUrl,
                                title = outline.text,
                            )
                        }.onSuccess {
                            added.incrementAndGet()
                        }.onFailure {
                            errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                            Timber.e(it)
                            failed.incrementAndGet()
                        }

                        progressFlow.value = FeedImportProgress(
                            imported = added.get() + exists.get() + failed.get(),
                            total = feeds.size,
                        )
                    }
                }.awaitAll()
            }
        }

        value = State.ShowingImportResult(
            FeedImportResult(
                added = added.get(),
                exists = exists.get(),
                failed = failed.get(),
                errors = errors,
            )
        )
    }

    private suspend fun changeState(action: suspend MutableStateFlow<State>.() -> Unit) {
        val initialState = state.value

        runCatching {
            action.invoke(state)
        }.onFailure {
            state.value = initialState
            throw it
        }
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
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapterItem>) : State()
        data class ImportingFeeds(val progress: MutableStateFlow<FeedImportProgress>) : State()
        data class ShowingImportResult(val result: FeedImportResult) : State()
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