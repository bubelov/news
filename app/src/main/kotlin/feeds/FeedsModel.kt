package feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.feedk.AtomLinkRel
import conf.ConfRepo
import db.Db
import db.Feed
import entries.EntriesRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import opml.exportOpml
import org.koin.android.annotation.KoinViewModel
import java.util.concurrent.atomic.AtomicInteger

@KoinViewModel
class FeedsModel(
    private val confRepo: ConfRepo,
    private val db: Db,
    private val entriesRepo: EntriesRepo,
    private val feedsRepo: FeedsRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val actionInProgress = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<ImportProgress?>(null)

    init {
        combine(
            feedsRepo.selectAll(),
            actionInProgress,
            importProgress,
        ) { feeds, actionInProgress, importProgress ->
            if (importProgress != null) {
                _state.update { State.ImportingFeeds(importProgress) }
                return@combine
            }

            if (actionInProgress) {
                _state.update { State.Loading }
                return@combine
            }

            withContext(Dispatchers.Default) {
                db.transaction {
                    val items = feeds.map { it.toItem(entriesRepo.selectUnreadCount(it.id)) }
                    _state.update { State.ShowingFeeds(items) }
                }
            }
        }.launchIn(viewModelScope)
    }

    suspend fun importOpml(opmlDocument: String): ImportResult {
        actionInProgress.update { true }

        return runCatching {
            val opmlFeeds = opml.importOpml(opmlDocument)
            importProgress.update { ImportProgress(0, opmlFeeds.size) }

            val added = AtomicInteger()
            val exists = AtomicInteger()
            val failed = AtomicInteger()
            val errors = mutableListOf<String>()

            val existingLinks = feedsRepo.selectLinks().first()

            opmlFeeds.forEach { outline ->
                val outlineUrl = outline.xmlUrl.toHttpUrl()

                val feedAlreadyExists = existingLinks.any {
                    it.href.toUri().normalize() == outlineUrl.toUri().normalize()
                }

                if (feedAlreadyExists) {
                    exists.incrementAndGet()
                } else {
                    runCatching {
                        feedsRepo.insertByUrl(outlineUrl)
                    }.onSuccess {
                        added.incrementAndGet()
                    }.onFailure {
                        errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                        failed.incrementAndGet()
                    }

                    importProgress.update {
                        ImportProgress(
                            imported = added.get() + exists.get() + failed.get(),
                            total = opmlFeeds.size,
                        )
                    }
                }
            }

            ImportResult(
                feedsAdded = added.get(),
                feedsUpdated = exists.get(),
                feedsFailed = failed.get(),
                errors = errors,
            )
        }.onSuccess {
            importProgress.update { null }
            actionInProgress.update { false }
        }.getOrElse {
            importProgress.update { null }
            actionInProgress.update { false }
            throw it
        }
    }

    suspend fun exportOpml(): ByteArray {
        val feeds = feedsRepo.selectAll().first()
        return exportOpml(feeds).toByteArray()
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

    private fun Feed.toItem(unreadCount: Long): FeedsAdapter.Item {
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = links.single { it.rel is AtomLinkRel.Self }.href,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = unreadCount,
            confUseBuiltInBrowser = confRepo.conf.value.useBuiltInBrowser,
        )
    }

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: ImportProgress) : State()
    }

    data class ImportProgress(
        val imported: Int,
        val total: Int,
    )

    data class ImportResult(
        val feedsAdded: Int,
        val feedsUpdated: Int,
        val feedsFailed: Int,
        val errors: List<String>,
    )
}