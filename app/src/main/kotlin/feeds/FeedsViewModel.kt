package feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.ConfRepository
import db.Conf
import db.Feed
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import links.LinksRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import opml.exportOpml
import java.util.concurrent.atomic.AtomicInteger

class FeedsViewModel(
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
    private val linksRepo: LinksRepository,
    confRepo: ConfRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    private val showProgress = MutableStateFlow(false)
    private val opmlImportProgress = MutableStateFlow<OpmlImportProgress?>(null)

    init {
        _state.update { State.Loading }

        combine(
            feedsRepo.selectAll(),
            confRepo.select(),
            showProgress,
            opmlImportProgress,
        ) { feeds, conf, showProgress, opmlImportProgress ->
            if (opmlImportProgress != null) {
                _state.update { State.ImportingOpml(opmlImportProgress) }
            } else {
                if (showProgress) {
                    _state.update { State.Loading }
                } else {
                    _state.update {
                        State.Loaded(
                            feeds = feeds.map { it.toItem() },
                            conf = conf,
                        )
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

//    suspend fun onViewCreated() {
//        conf = confRepo.select().first()
//
//        if (state.value == null) {
//            reload()
//        }
//    }

//    suspend fun reload() = state.apply {
//        value = State.Loading
//        value = State.Loaded(runCatching { feedsRepo.selectAll().first().map { it.toItem() } })
//    }

    suspend fun importOpml(opmlDocument: String): OpmlImportResult {
        opmlImportProgress.update { OpmlImportProgress(0, -1) }

        return runCatching {
            val opmlFeeds = opml.importOpml(opmlDocument)
            opmlImportProgress.update { OpmlImportProgress(0, opmlFeeds.size) }

            val added = AtomicInteger()
            val exists = AtomicInteger()
            val failed = AtomicInteger()
            val errors = mutableListOf<String>()

            withContext(Dispatchers.Default) {
                val cachedFeeds = feedsRepo.selectAll().first()

                opmlFeeds.chunked(10).forEach { chunk ->
                    chunk.map { outline ->
                        async {
                            //val cachedFeed = cachedFeeds.firstOrNull { it.selfLink == outline.xmlUrl }
                            val cachedFeed: Feed? = null

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
                                val feed = feedsRepo.insertByUrl(outline.xmlUrl.toHttpUrl())

                                feedsRepo.updateTitle(
                                    feedId = feed.id,
                                    newTitle = outline.text,
                                )
                            }.onSuccess {
                                added.incrementAndGet()
                            }.onFailure {
                                errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                                failed.incrementAndGet()
                            }

                            opmlImportProgress.update {
                                OpmlImportProgress(
                                    imported = added.get() + exists.get() + failed.get(),
                                    total = opmlFeeds.size,
                                )
                            }
                        }
                    }.awaitAll()
                }

                OpmlImportResult(
                    feedsAdded = added.get(),
                    feedsUpdated = exists.get(),
                    feedsFailed = failed.get(),
                    errors = errors,
                )
            }
        }.onSuccess {
            opmlImportProgress.update { null }
        }.getOrElse {
            opmlImportProgress.update { null }
            throw it
        }
    }

    suspend fun exportAsOpml(): ByteArray {
        return withContext(Dispatchers.Default) {
            val feedsWithLinks = feedsRepo.selectAll().first().map {
                Pair(it, linksRepo.selectByFeedId(it.id).first())
            }

            exportOpml(feedsWithLinks).toByteArray()
        }
    }

    suspend fun addFeed(url: String) {
        withContext(Dispatchers.Default) {
            showProgress.update { true }
            val fullUrl = if (!url.startsWith("http://")) "https://$url" else url
            val parsedUrl = fullUrl.toHttpUrl()
            delay(2500)
            feedsRepo.insertByUrl(parsedUrl)
            showProgress.update { false }
        }
    }

    fun rename(feedId: String, newTitle: String) {
        viewModelScope.launch { feedsRepo.updateTitle(feedId, newTitle) }
    }

    fun delete(feedId: String) {
        viewModelScope.launch { feedsRepo.deleteById(feedId) }
    }

    private suspend fun Feed.toItem() = FeedsAdapter.Item(
        id = id,
        title = title,
        selfLink = "",
        alternateLink = "",
        unreadCount = entriesRepo.getUnreadCount(id).first(),
    )

    sealed class State {
        object Loading : State()

        data class Loaded(
            val feeds: List<FeedsAdapter.Item>,
            val conf: Conf,
        ) : State()

        data class ImportingOpml(val progress: OpmlImportProgress) : State()
    }

    data class OpmlImportProgress(
        val imported: Int,
        val total: Int,
    )

    data class OpmlImportResult(
        val feedsAdded: Int,
        val feedsUpdated: Int,
        val feedsFailed: Int,
        val errors: List<String>,
    )
}