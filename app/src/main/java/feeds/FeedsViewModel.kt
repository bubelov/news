package feeds

import androidx.lifecycle.ViewModel
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
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class FeedsViewModel(
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
) : ViewModel() {

    val state = MutableStateFlow<State?>(null)

    suspend fun onViewReady() {
        if (state.value == null) {
            reload()
        }
    }

    suspend fun reload() = state.apply {
        value = State.Loading
        value = State.Loaded(runCatching { feedsRepo.selectAll().map { it.toItem() } })
    }

    suspend fun addMany(opmlDocument: String) = state.apply {
        val progress = MutableStateFlow(ImportProgress(0, -1))
        value = State.AddingMany(progress)

        val feeds = runCatching {
            importOpml(opmlDocument)
        }.getOrElse {
            value = State.AddedMany(
                ImportResult(
                    parsed = false,
                    added = -1,
                    exists = -1,
                    failed = -1,
                    errors = listOf("Can't parse OPML file: ${it.message}"),
                )
            )
            return@apply
        }

        val added = AtomicInteger()
        val exists = AtomicInteger()
        val failed = AtomicInteger()
        val errors = mutableListOf<String>()

        progress.value = progress.value.copy(total = feeds.size)
        val cachedFeeds = feedsRepo.selectAll()

        withContext(Dispatchers.IO) {
            feeds.chunked(10).forEach { chunk ->
                chunk.map { outline ->
                    async {
                        val cachedFeed = cachedFeeds.firstOrNull { it.selfLink == outline.xmlUrl }

                        if (cachedFeed != null) {
                            feedsRepo.insertOrReplace(
                                cachedFeed.copy(
                                    openEntriesInBrowser = outline.openEntriesInBrowser,
                                    blockedWords = outline.blockedWords,
                                    showPreviewImages = outline.showPreviewImages,
                                )
                            )

                            exists.incrementAndGet()
                            return@async
                        }

                        runCatching {
                            feedsRepo.insertByFeedUrl(
                                url = URI.create(outline.xmlUrl).toURL(),
                                title = outline.text,
                            )
                        }.onSuccess {
                            added.incrementAndGet()
                        }.onFailure {
                            errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                            Timber.e(it)
                            failed.incrementAndGet()
                        }

                        progress.value = progress.value.copy(
                            imported = added.get() + exists.get() + failed.get(),
                        )
                    }
                }.awaitAll()
            }
        }

        value = State.AddedMany(
            ImportResult(
                parsed = true,
                added = added.get(),
                exists = exists.get(),
                failed = failed.get(),
                errors = errors,
            )
        )
    }

    suspend fun exportAsOpml(): ByteArray {
        return exportOpml(feedsRepo.selectAll()).toByteArray()
    }

    suspend fun addOne(url: URL) = state.apply {
        value = State.AddingOne
        value = State.AddedOne(runCatching { feedsRepo.insertByFeedUrl(url) })
    }

    suspend fun rename(feedId: String, newTitle: String) = state.apply {
        value = State.Renaming
        value = State.Renamed(runCatching { feedsRepo.updateTitle(feedId, newTitle) })
    }

    suspend fun delete(feedId: String) = state.apply {
        value = State.Deleting

        value = State.Deleted(runCatching {
            feedsRepo.deleteById(feedId)
            entriesRepo.deleteByFeedId(feedId)
        })
    }

    private suspend fun Feed.toItem(): FeedsAdapterItem = FeedsAdapterItem(
        id = id,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
        unreadCount = entriesRepo.getUnreadCount(id),
    )

    sealed class State {
        object Loading : State()
        data class Loaded(val result: Result<List<FeedsAdapterItem>>) : State()
        object AddingOne : State()
        data class AddedOne(val result: Result<Any>) : State()
        data class AddingMany(val progress: MutableStateFlow<ImportProgress>) : State()
        data class AddedMany(val result: ImportResult) : State()
        object Renaming : State()
        data class Renamed(val result: Result<Any>) : State()
        object Deleting : State()
        data class Deleted(val result: Result<Any>) : State()
    }

    data class ImportProgress(
        val imported: Int,
        val total: Int,
    )

    data class ImportResult(
        val parsed: Boolean,
        val added: Int,
        val exists: Int,
        val failed: Int,
        val errors: List<String>,
    )
}