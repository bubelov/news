package entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import conf.ConfRepo.Companion.SORT_ORDER_ASCENDING
import conf.ConfRepo.Companion.SORT_ORDER_DESCENDING
import db.Conf
import db.EntryWithoutContent
import db.Feed
import feeds.FeedsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    private var scrollToTopNextTime = false

    init {
        args.onEach { filter ->
            if (filter == null) {
                return@onEach
            }

            _state.update { State.LoadingCachedEntries }

            confRepo.conf.value.apply {
                if (!initialSyncCompleted || (syncOnStartup && !syncedOnStartup)) {
                    viewModelScope.launch {
                        newsApiSync.run()
                        confRepo.update { it.copy(syncedOnStartup = true) }
                    }
                }
            }

            combine(
                confRepo.conf,
                feedsRepo.selectAll(),
                entriesRepo.selectCount(),
                newsApiSync.state,
            ) { conf, feeds, _, syncState ->
                when (syncState) {
                    is Sync.State.InitialSync -> State.InitialSync(syncState.message)
                    else -> {
                        val showBgProgress = when (syncState) {
                            is Sync.State.Idle -> false
                            is Sync.State.InitialSync -> false
                            is Sync.State.FollowUpSync -> syncState.args.syncEntries
                        }

                        val scrollToTop = scrollToTopNextTime
                        scrollToTopNextTime = false

                        State.ShowingCachedEntries(
                            entries = selectEntries(filter, feeds, conf),
                            showBackgroundProgress = showBgProgress,
                            scrollToTop = scrollToTop,
                        )
                    }
                }
            }.collect { state -> _state.update { state } }
        }.launchIn(viewModelScope)
    }

    suspend fun onRetry() {
        viewModelScope.launch { newsApiSync.run() }
    }

    private suspend fun selectEntries(
        filter: EntriesFilter,
        feeds: List<Feed>,
        conf: Conf,
    ): List<EntriesAdapterItem> {
        val unsortedEntries = when (filter) {
            is EntriesFilter.NotBookmarked -> {
                if (conf.showReadEntries) {
                    entriesRepo.selectAll().first()
                } else {
                    entriesRepo.selectByRead(false).first()
                }.filterNot { it.bookmarked }
            }

            is EntriesFilter.Bookmarked -> {
                entriesRepo.getBookmarked().first()
            }

            is EntriesFilter.BelongToFeed -> {
                val feedEntries = entriesRepo.selectByFeedId(filter.feedId).first()

                if (conf.showReadEntries) {
                    feedEntries
                } else {
                    feedEntries.filter { !it.read }
                }
            }
        }

        val sortedEntries = when (conf.sortOrder) {
            SORT_ORDER_ASCENDING -> unsortedEntries.sortedBy { it.published }
            SORT_ORDER_DESCENDING -> unsortedEntries.sortedByDescending { it.published }
            else -> unsortedEntries
        }

        val rows = withContext(Dispatchers.Default) {
            sortedEntries.map { entry ->
                val feed = feeds.single { feed -> feed.id == entry.feedId }
                entry.toRow(feed, conf)
            }
        }

        return rows
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

    fun getEntry(id: String) = entriesRepo.selectById(id)

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

    private fun EntryWithoutContent.toRow(
        feed: Feed,
        conf: Conf,
    ): EntriesAdapterItem {
        return EntriesAdapterItem(
            entry = this,
            feed = feed,
            conf = conf,
            showImage = conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            title = title,
            subtitle = "${feed.title} · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = read,
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