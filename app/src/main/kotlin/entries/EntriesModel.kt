package entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import conf.ConfRepo.Companion.SORT_ORDER_ASCENDING
import conf.ConfRepo.Companion.SORT_ORDER_DESCENDING
import db.Conf
import db.SelectByFeedIdAndReadAndBookmarked
import db.SelectByReadAndBookmarked
import feeds.FeedsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import sync.Sync
import sync.SyncResult
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class EntriesModel(
    private val confRepo: ConfRepo,
    private val entriesRepo: EntriesRepo,
    private val feedsRepo: FeedsRepo,
    private val newsApiSync: Sync,
) : ViewModel() {

    val args = MutableStateFlow<EntriesFilter?>(null)

    private val _state = MutableStateFlow<State>(State.LoadingCachedEntries)
    val state = _state.asStateFlow()

    private var scrollToTopNextTime = false

    init {
        args.filterNotNull().onEach { filter ->
            combine(
                confRepo.conf,
                entriesRepo.selectCount(),
                newsApiSync.state,
            ) { conf, _, syncState ->
                if (!conf.initialSyncCompleted || (conf.syncOnStartup && !conf.syncedOnStartup)) {
                    confRepo.update { it.copy(syncedOnStartup = true) }
                    viewModelScope.launch { newsApiSync.run() }
                }

                val newState = when (syncState) {
                    is Sync.State.InitialSync -> State.InitialSync(syncState.message)

                    else -> {
                        val showBgProgress = when (syncState) {
                            is Sync.State.FollowUpSync -> syncState.args.syncEntries
                            else -> false
                        }

                        val scrollToTop = scrollToTopNextTime
                        scrollToTopNextTime = false

                        State.ShowingCachedEntries(
                            entries = if (filter is EntriesFilter.BelongToFeed) selectByFeedIdAndReadAndBookmarked(
                                feedId = filter.feedId,
                                bookmarked = filter is EntriesFilter.Bookmarked,
                                conf = conf,
                            ) else {
                                selectByReadAndBookmarked(
                                    bookmarked = filter is EntriesFilter.Bookmarked,
                                    conf = conf,
                                )
                            },
                            showBackgroundProgress = showBgProgress,
                            scrollToTop = scrollToTop,
                        )
                    }
                }

                _state.update { newState }
            }.collect()
        }.launchIn(viewModelScope)
    }

    fun onRetry() {
        viewModelScope.launch { newsApiSync.run() }
    }

    private suspend fun selectByFeedIdAndReadAndBookmarked(
        feedId: String,
        bookmarked: Boolean,
        conf: Conf,
    ): List<EntriesAdapterItem> {
        val unsorted = entriesRepo.selectByFeedIdAndReadAndBookmarked(
            feedId = feedId,
            read = if (conf.showReadEntries) listOf(true, false) else listOf(false),
            bookmarked = bookmarked,
        ).first()

        val sorted = when (conf.sortOrder) {
            SORT_ORDER_ASCENDING -> unsorted.sortedBy { it.published }
            SORT_ORDER_DESCENDING -> unsorted.sortedByDescending { it.published }
            else -> throw Exception()
        }

        return sorted.map { it.toRow(conf) }
    }

    private suspend fun selectByReadAndBookmarked(
        bookmarked: Boolean,
        conf: Conf,
    ): List<EntriesAdapterItem> {
        val entries = entriesRepo.selectByReadAndBookmarked(
            read = if (conf.showReadEntries) listOf(true, false) else listOf(false),
            bookmarked = bookmarked,
        ).first()

        val sortedEntries = withContext(Dispatchers.Default) {
            when (conf.sortOrder) {
                SORT_ORDER_ASCENDING -> entries.sortedBy { it.published }
                SORT_ORDER_DESCENDING -> entries.sortedByDescending { it.published }
                else -> throw Exception()
            }
        }

        return withContext(Dispatchers.Default) { sortedEntries.map { it.toRow(conf) } }
    }

    suspend fun onPullRefresh() {
        val syncResult = newsApiSync.run()
        if (syncResult is SyncResult.Failure) throw syncResult.cause
    }

    fun loadConf() = confRepo.conf

    fun saveConf(newConf: (Conf) -> Conf) {
        this.confRepo.update(newConf)
    }

    fun changeSortOrder() {
        confRepo.update {
            val newSortOrder = when (it.sortOrder) {
                SORT_ORDER_ASCENDING -> SORT_ORDER_DESCENDING
                SORT_ORDER_DESCENDING -> SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            it.copy(sortOrder = newSortOrder)
        }

        scrollToTopNextTime = true
    }

    fun getFeed(id: String) = feedsRepo.selectById(id)

    fun setRead(entryIds: Collection<String>, value: Boolean) {
        viewModelScope.launch {
            entryIds.forEach { entriesRepo.setRead(it, value, false) }

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        viewModelScope.launch {
            entriesRepo.setBookmarked(entryId, bookmarked, false)

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    suspend fun markAllAsRead() {
        when (val filter = args.value) {
            null -> {}

            is EntriesFilter.NotBookmarked -> {
                entriesRepo.updateReadByBookmarked(
                    read = true,
                    bookmarked = false,
                )
            }

            is EntriesFilter.Bookmarked -> {
                entriesRepo.updateReadByBookmarked(
                    read = true,
                    bookmarked = true,
                )
            }

            is EntriesFilter.BelongToFeed -> {
                entriesRepo.updateReadByFeedId(
                    read = true,
                    feedId = filter.feedId,
                )
            }
        }

        viewModelScope.launch {
            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun SelectByFeedIdAndReadAndBookmarked.toRow(conf: Conf): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            showImage = showPreviewImages ?: conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = ogImageUrl,
            imageWidth = ogImageWidth.toInt(),
            imageHeight = ogImageHeight.toInt(),
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = read,
            openInBrowser = openEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    private fun SelectByReadAndBookmarked.toRow(conf: Conf): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            showImage = showPreviewImages ?: conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = ogImageUrl,
            imageWidth = ogImageWidth.toInt(),
            imageHeight = ogImageHeight.toInt(),
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = read,
            openInBrowser = openEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    sealed class State {

        data class InitialSync(val message: String) : State()

        object LoadingCachedEntries : State()

        data class ShowingCachedEntries(
            val entries: List<EntriesAdapterItem>,
            val showBackgroundProgress: Boolean,
            val scrollToTop: Boolean = false,
        ) : State()

        data class FailedToSync(val cause: Throwable) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}