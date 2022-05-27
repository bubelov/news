package feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import db.Database
import db.Feed
import db.Link
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
    private val db: Database,
    private val api: NewsApi,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.LoadingFeeds)
    val state = _state.asStateFlow()

    private val showProgress = MutableStateFlow(false)
    private val opmlImportProgress = MutableStateFlow<OpmlImportProgress?>(null)

    init {
        _state.update { State.LoadingFeeds }

        combine(
            db.feedQueries.selectAll().asFlow().mapToList(),
            db.linkQueries.selectByEntryid(null).asFlow().mapToList(),
            showProgress,
            opmlImportProgress,
        ) { feeds, feedLinks, showProgress, opmlImportProgress ->
            if (opmlImportProgress != null) {
                _state.update { State.ImportingFeeds(opmlImportProgress) }
            } else {
                if (showProgress) {
                    _state.update { State.LoadingFeeds }
                } else {
                    _state.update {
                        State.ShowingFeeds(
                            feeds = feeds.map { feed ->
                                feed.toItem(
                                    feedLinks.filter { it.feedId == feed.id },
                                    db.entryQueries.selectUnreadCount(feed.id).executeAsOne(),
                                )
                            }
                        )
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    suspend fun importOpml(opmlDocument: String): OpmlImportResult {
        opmlImportProgress.update { OpmlImportProgress(0, -1) }

        return runCatching {
            val opmlFeeds = opml.importOpml(opmlDocument)
            opmlImportProgress.update { OpmlImportProgress(0, opmlFeeds.size) }

            val added = AtomicInteger()
            val exists = AtomicInteger()
            val failed = AtomicInteger()
            val errors = mutableListOf<String>()

            val existingFeeds = db.feedQueries.selectAll().asFlow().mapToList().first()

            opmlFeeds.forEach {

            }

//            withContext(Dispatchers.Default) {
//                val cachedFeeds = feedsRepo.selectAll().first()
//
//                opmlFeeds.chunked(10).forEach { chunk ->
//                    chunk.map { outline ->
//                        async {
//                            //val cachedFeed = cachedFeeds.firstOrNull { it.selfLink == outline.xmlUrl }
//                            val cachedFeed: Feed? = null
//
//                            if (cachedFeed != null) {
//                                feedsRepo.insertOrReplace(
//                                    cachedFeed.copy(
//                                        openEntriesInBrowser = outline.openEntriesInBrowser,
//                                        blockedWords = outline.blockedWords,
//                                        showPreviewImages = outline.showPreviewImages,
//                                    )
//                                )
//
//                                exists.incrementAndGet()
//                                return@async
//                            }
//
//                            runCatching {
//                                val feed = feedsRepo.insertByUrl(outline.xmlUrl.toHttpUrl())
//
//                                feedsRepo.updateTitle(
//                                    feedId = feed.id,
//                                    newTitle = outline.text,
//                                )
//                            }.onSuccess {
//                                added.incrementAndGet()
//                            }.onFailure {
//                                errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
//                                failed.incrementAndGet()
//                            }
//
//                            opmlImportProgress.update {
//                                OpmlImportProgress(
//                                    imported = added.get() + exists.get() + failed.get(),
//                                    total = opmlFeeds.size,
//                                )
//                            }
//                        }
//                    }.awaitAll()
//                }
//
//            }

            OpmlImportResult(
                feedsAdded = added.get(),
                feedsUpdated = exists.get(),
                feedsFailed = failed.get(),
                errors = errors,
            )
        }.onSuccess {
            opmlImportProgress.update { null }
        }.getOrElse {
            opmlImportProgress.update { null }
            throw it
        }
    }

    suspend fun exportOpml(): ByteArray {
        val feeds = db.feedQueries.selectAll().asFlow().mapToList().first()
        val links = db.linkQueries.selectByEntryid(null).asFlow().mapToList().first()
        val feedsWithLinks = feeds.map { feed -> Pair(feed, links.filter { it.feedId == feed.id }) }
        return exportOpml(feedsWithLinks).toByteArray()
    }

    suspend fun addFeed(url: String) {
        showProgress.update { true }
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url

        runCatching {
            withContext(Dispatchers.Default) {
                val feed = api.addFeed(fullUrl.toHttpUrl()).getOrThrow()

                db.transaction {
                    db.linkQueries.deleteByFeedId(feed.first.id)
                    db.feedQueries.insertOrReplace(feed.first)
                    feed.second.forEach { db.linkQueries.insertOrReplace(it) }
                }
            }
        }.onSuccess {
            showProgress.update { false }
        }.onFailure {
            showProgress.update { false }
        }.getOrThrow()
    }

    suspend fun rename(feedId: String, newTitle: String) {
        withContext(Dispatchers.Default) {
            val feed = db.feedQueries.selectById(feedId).asFlow().mapToOne().first()
            val trimmedNewTitle = newTitle.trim()
            api.updateFeedTitle(feedId, trimmedNewTitle)

            withContext(Dispatchers.Default) {
                db.feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
            }
        }
    }

    suspend fun delete(feedId: String) {
        showProgress.update { true }

        runCatching {
            withContext(Dispatchers.Default) {
                api.deleteFeed(feedId)

                db.transaction {
                    db.linkQueries.deleteByFeedId(feedId)
                    db.feedQueries.deleteById(feedId)
                    val entries = db.entryQueries.selectByFeedId(feedId).executeAsList()
                    entries.forEach { db.linkQueries.selectByEntryid(it.id) }
                    db.entryQueries.deleteByFeedId(feedId)
                }
            }
        }.onSuccess {
            showProgress.update { false }
        }.onFailure {
            showProgress.update { false }
        }.getOrThrow()
    }

    private fun Feed.toItem(links: List<Link>, unreadCount: Long): FeedsAdapter.Item {
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = links.firstOrNull { it.rel == "self" }?.href?.toString() ?: "",
            alternateLink = links.firstOrNull { it.rel == "alternate" }?.href?.toString() ?: "",
            unreadCount = unreadCount,
        )
    }

    sealed class State {
        object LoadingFeeds : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: OpmlImportProgress) : State()
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