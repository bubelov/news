package feeds

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import co.appreactor.news.R
import common.ConfRepository
import db.Conf
import db.Feed
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import opml.exportOpml
import opml.importOpml
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class FeedsViewModel(
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
    private val confRepository: ConfRepository,
    private val resources: Resources,
) : ViewModel() {

    val state = MutableStateFlow<State?>(null)

    lateinit var conf: Conf

    suspend fun onViewCreated() {
        conf = confRepository.get()

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
        value = State.ImportingOpml(progress)

        val feeds = runCatching {
            importOpml(opmlDocument)
        }.getOrElse {
            value = State.ImportedOpml(OpmlImportResult.FailedToParse(it.message ?: ""))
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
                                url = outline.xmlUrl.toHttpUrl(),
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

        value = State.ImportedOpml(
            OpmlImportResult.Imported(
                feedsAdded = added.get(),
                feedsUpdated = exists.get(),
                feedsFailed = failed.get(),
                errors = errors,
            )
        )
    }

    suspend fun exportAsOpml(): ByteArray {
        return exportOpml(feedsRepo.selectAll()).toByteArray()
    }

    suspend fun addFeed(url: String) {
        val fullUrl =
            if (url.startsWith("http://") || !url.startsWith("https://")) "https://$url" else url

        runCatching {
            val parsedUrl = runCatching {
                fullUrl.toHttpUrl()
            }.getOrElse {
                throw Exception(resources.getString(R.string.invalid_url_s, fullUrl))
            }

            state.value = State.AddingOne
            state.value = State.AddedOne(runCatching { feedsRepo.insertByFeedUrl(parsedUrl) })
        }.onFailure {
            state.value = State.AddedOne(Result.failure(it))
        }
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
        data class ImportingOpml(val progress: MutableStateFlow<ImportProgress>) : State()
        data class ImportedOpml(val result: OpmlImportResult) : State()
        object Renaming : State()
        data class Renamed(val result: Result<Any>) : State()
        object Deleting : State()
        data class Deleted(val result: Result<Any>) : State()
    }

    data class ImportProgress(
        val imported: Int,
        val total: Int,
    )

    sealed class OpmlImportResult {
        data class FailedToParse(val reason: String) : OpmlImportResult()

        data class Imported(
            val feedsAdded: Int,
            val feedsUpdated: Int,
            val feedsFailed: Int,
            val errors: List<String>,
        ) : OpmlImportResult()
    }
}