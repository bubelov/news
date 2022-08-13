package feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.feedk.AtomLinkRel
import conf.ConfRepo
import db.SelectAllWithUnreadEntryCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import opml.OpmlDocument
import opml.OpmlOutline
import opml.OpmlVersion
import opml.leafOutlines
import opml.toOpml
import opml.toPrettyString
import opml.toXmlDocument
import org.koin.android.annotation.KoinViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory

@KoinViewModel
class FeedsModel(
    private val confRepo: ConfRepo,
    private val feedsRepo: FeedsRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val hasActionInProgress = MutableStateFlow(false)
    private val importState = MutableStateFlow<ImportState?>(null)
    private val error = MutableStateFlow<Throwable?>(null)

    init {
        combine(
            feedsRepo.selectAllWithUnreadEntryCount(),
            hasActionInProgress,
            importState,
            error,
        ) { feeds, hasActionInProgress, importState, error ->
            if (error != null) {
                _state.update { State.ShowingError(error) }
                return@combine
            }

            if (importState != null) {
                _state.update { State.ImportingFeeds(importState) }
                return@combine
            }

            if (hasActionInProgress) {
                _state.update { State.Loading }
                return@combine
            }

            _state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
        }.launchIn(viewModelScope)
    }

    fun importOpml(document: InputStream) {
        viewModelScope.launch {
            val outlines = runCatching {
                withContext(Dispatchers.Default) {
                    DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(document)
                        .toOpml()
                        .leafOutlines()
                }
            }.getOrElse { e ->
                error.update { e }
                return@launch
            }

            importState.update { ImportState(0, outlines.size) }

            var feedsImported = 0
            var feedsExisted = 0
            var feedsFailed = 0
            val errors = mutableListOf<String>()

            val mutex = Mutex()

            val outlinesChannel = produce { outlines.forEach { send(it) } }
            val existingLinks = feedsRepo.selectLinks().first()

            val workers = buildList {
                repeat(15) {
                    add(
                        async {
                            for (outline in outlinesChannel) {
                                val outlineUrl = (outline.xmlUrl ?: "").toHttpUrlOrNull()

                                if (outlineUrl == null) {
                                    mutex.withLock {
                                        errors += "Invalid URL: ${outline.xmlUrl}"
                                        feedsFailed++
                                    }

                                    continue
                                }

                                val feedAlreadyExists = existingLinks.any {
                                    it.href.toUri().normalize() == outlineUrl.toUri().normalize()
                                }

                                if (feedAlreadyExists) {
                                    mutex.withLock { feedsExisted++ }
                                } else {
                                    runCatching {
                                        feedsRepo.insertByUrl(outlineUrl)
                                    }.onSuccess {
                                        mutex.withLock { feedsImported++ }
                                    }.onFailure {
                                        mutex.withLock {
                                            errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                                            feedsFailed++
                                        }
                                    }

                                    importState.update {
                                        ImportState(
                                            imported = feedsImported + feedsExisted + feedsFailed,
                                            total = outlines.size,
                                        )
                                    }
                                }
                            }
                        }

                    )
                }
            }

            workers.awaitAll()

            if (errors.isNotEmpty()) {
                val message = buildString {
                    errors.forEach {
                        append(it)

                        if (errors.last() != it) {
                            append("\n\n")
                        }
                    }
                }

                error.update { Exception(message) }
            }

            importState.update { null }
        }
    }

    fun exportOpml(out: OutputStream) {
        viewModelScope.launch {
            val feeds = feedsRepo.selectAll().first()

            val outlines = feeds.map { feed ->
                OpmlOutline(
                    text = feed.title,
                    outlines = emptyList(),
                    xmlUrl = feed.links.first { it.rel is AtomLinkRel.Self }.href.toString(),
                    htmlUrl = feed.links.first { it.rel is AtomLinkRel.Alternate }.href.toString(),
                    extOpenEntriesInBrowser = feed.ext_open_entries_in_browser,
                    extShowPreviewImages = feed.ext_show_preview_images,
                    extBlockedWords = feed.ext_blocked_words,
                )
            }

            val opmlDocument = OpmlDocument(
                version = OpmlVersion.V_2_0,
                outlines = outlines,
            )

            withContext(Dispatchers.Default) {
                out.write(opmlDocument.toXmlDocument().toPrettyString().toByteArray())
            }
        }
    }

    fun onErrorAcknowledged() {
        error.update { null }
    }

    fun addFeed(url: String) {
        viewModelScope.launch {
            runCatching {
                hasActionInProgress.update { true }
                val fullUrl = if (!url.startsWith("http")) "https://$url" else url
                feedsRepo.insertByUrl(fullUrl.toHttpUrl())
            }.onFailure { e -> error.update { e } }

            hasActionInProgress.update { false }
        }
    }

    fun renameFeed(feedId: String, newTitle: String) {
        viewModelScope.launch {
            runCatching {
                hasActionInProgress.update { true }
                feedsRepo.updateTitle(feedId, newTitle)
            }.onFailure { e -> error.update { e } }

            hasActionInProgress.update { false }
        }
    }

    fun deleteFeed(feedId: String) {
        viewModelScope.launch {
            runCatching {
                hasActionInProgress.update { true }
                feedsRepo.deleteById(feedId)
            }.onFailure { e -> error.update { e } }

            hasActionInProgress.update { false }
        }
    }

    private fun SelectAllWithUnreadEntryCount.toItem(): FeedsAdapter.Item {
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = links.single { it.rel is AtomLinkRel.Self }.href,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = unread_entries,
            confUseBuiltInBrowser = confRepo.conf.value.useBuiltInBrowser,
        )
    }

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: ImportState) : State()
        data class ShowingError(val error: Throwable) : State()
    }

    data class ImportState(
        val imported: Int,
        val total: Int,
    )
}