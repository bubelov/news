package feeds

import android.util.Log
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
import org.koin.android.annotation.KoinViewModel
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

@KoinViewModel
class FeedsModel(
    private val confRepo: ConfRepo,
    private val feedsRepo: FeedsRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val actionInProgress = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<ImportProgress?>(null)
    private val importErrors = MutableStateFlow<Collection<String>>(emptyList())

    init {
        combine(
            feedsRepo.selectAllWithUnreadEntryCount(),
            actionInProgress,
            importProgress,
            importErrors,
        ) { feeds, actionInProgress, importProgress, importErrors ->
            if (importErrors.isNotEmpty()) {
                _state.update { State.ShowingImportErrors(importErrors) }
                return@combine
            }

            if (importProgress != null) {
                _state.update { State.ImportingFeeds(importProgress) }
                return@combine
            }

            if (actionInProgress) {
                _state.update { State.Loading }
                return@combine
            }

            _state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
        }.launchIn(viewModelScope)
    }

    fun onImportErrorsAcknowledged() {
        importErrors.update { emptyList() }
    }

    fun importOpml(document: InputStream) {
        actionInProgress.update { true }

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
            }.getOrElse { error ->
                importErrors.update { listOf(error.message ?: "") }
                return@launch
            }

            importProgress.update { ImportProgress(0, outlines.size) }

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

                                    importProgress.update {
                                        ImportProgress(
                                            imported = feedsImported + feedsExisted + feedsFailed,
                                            total = outlines.size,
                                        )
                                    }

                                    Log.d("feeds", "Progress: ${importProgress.value}")
                                    Log.d(
                                        "feeds",
                                        "Imported: $feedsImported, existed: $feedsExisted, failed: $feedsFailed"
                                    )
                                }
                            }
                        }

                    )
                }
            }

            workers.awaitAll()

            if (errors.isNotEmpty()) {
                importErrors.update { errors }
            }

            importProgress.update { null }
            actionInProgress.update { false }
        }
    }

    suspend fun generateOpml(): OpmlDocument {
        val outlines = feedsRepo.selectAll().first().map { feed ->
            OpmlOutline(
                text = feed.title,
                outlines = emptyList(),
                xmlUrl = feed.links.first { it.rel is AtomLinkRel.Self }.href.toString(),
                htmlUrl = feed.links.first { it.rel is AtomLinkRel.Alternate }.href.toString(),
                extOpenEntriesInBrowser = feed.openEntriesInBrowser,
                extShowPreviewImages = feed.showPreviewImages,
                extBlockedWords = feed.blockedWords,
            )
        }

        return OpmlDocument(
            version = OpmlVersion.V_2_0,
            outlines = outlines,
        )
    }

    suspend fun addFeed(url: String) {
        actionInProgress.update { true }
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url

        runCatching {
            feedsRepo.insertByUrl(fullUrl.toHttpUrl())
        }.onSuccess {
            actionInProgress.update { false }
        }.onFailure {
            actionInProgress.update { false }
        }.getOrThrow()
    }

    suspend fun rename(feedId: String, newTitle: String) {
        actionInProgress.update { true }

        runCatching {
            feedsRepo.updateTitle(feedId, newTitle)
        }.onSuccess {
            actionInProgress.update { false }
        }.onFailure {
            actionInProgress.update { false }
        }.getOrThrow()
    }

    suspend fun delete(feedId: String) {
        actionInProgress.update { true }

        runCatching {
            feedsRepo.deleteById(feedId)
        }.onSuccess {
            actionInProgress.update { false }
        }.onFailure {
            actionInProgress.update { false }
        }.getOrThrow()
    }

    private fun SelectAllWithUnreadEntryCount.toItem(): FeedsAdapter.Item {
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = links.single { it.rel is AtomLinkRel.Self }.href,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = unreadEntries,
            confUseBuiltInBrowser = confRepo.conf.value.useBuiltInBrowser,
        )
    }

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: ImportProgress) : State()
        data class ShowingImportErrors(val errors: Collection<String>) : State()
    }

    data class ImportProgress(
        val imported: Int,
        val total: Int,
    )
}