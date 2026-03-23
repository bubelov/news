package org.vestifeed.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.feedk.AtomLinkRel
import org.vestifeed.db.SelectAllWithUnreadEntryCount
import org.vestifeed.entries.EntriesRepo
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
import org.vestifeed.db.Db
import org.vestifeed.opml.OpmlDocument
import org.vestifeed.opml.OpmlOutline
import org.vestifeed.opml.OpmlVersion
import org.vestifeed.opml.leafOutlines
import org.vestifeed.opml.toOpml
import org.vestifeed.opml.toPrettyString
import org.vestifeed.opml.toXmlDocument
import java.io.InputStream
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory

class FeedsModel(
    private val db: Db,
    private val feedsRepo: FeedsRepo,
    private val entriesRepo: EntriesRepo,
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
                withContext(Dispatchers.IO) {
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
            val existingLinks = feedsRepo.selectLinks().first().flatten()

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
                val selfLink = feed.links.firstOrNull { it.rel is AtomLinkRel.Self } ?: feed.links.firstOrNull()
                OpmlOutline(
                    text = feed.title,
                    outlines = emptyList(),
                    xmlUrl = selfLink?.href?.toString() ?: "",
                    htmlUrl = feed.links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href?.toString(),
                    extOpenEntriesInBrowser = feed.extOpenEntriesInBrowser,
                    extShowPreviewImages = feed.extShowPreviewImages,
                    extBlockedWords = feed.extBlockedWords,
                )
            }

            val opmlDocument = OpmlDocument(
                version = OpmlVersion.V_2_0,
                outlines = outlines,
            )

            withContext(Dispatchers.IO) {
                out.write(opmlDocument.toXmlDocument().toPrettyString().toByteArray())
            }
        }
    }

    fun onErrorAcknowledged() {
        error.update { null }
    }

    fun addFeed(unvalidatedUrl: String) {
        viewModelScope.launch {
            runCatching {
                hasActionInProgress.update { true }

                val hasHttpPrefix = unvalidatedUrl.startsWith("http") or unvalidatedUrl.startsWith("https")

                val entries = if (hasHttpPrefix) {
                    feedsRepo.insertByUrl(unvalidatedUrl.toHttpUrl()).second
                } else {
                    feedsRepo.insertByUrl("https://$unvalidatedUrl".toHttpUrl()).second
                }

                entriesRepo.insertOrReplace(entries)
            }.onSuccess {
                hasActionInProgress.update { false }
            }.onFailure { e ->
                hasActionInProgress.update { false }
                error.update { e }
            }
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
        val selfLink = links.firstOrNull { it.rel is AtomLinkRel.Self }?.href 
            ?: links.firstOrNull()?.href
            ?: "https://example.com".toHttpUrl()
        
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = selfLink,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = unreadEntries,
            confUseBuiltInBrowser = db.confQueries.select().useBuiltInBrowser,
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