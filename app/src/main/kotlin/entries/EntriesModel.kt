package entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import conf.ConfRepo.Companion.SORT_ORDER_ASCENDING
import conf.ConfRepo.Companion.SORT_ORDER_DESCENDING
import db.Conf
import db.EntriesAdapterRow
import db.Feed
import feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.Sync
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
        viewModelScope.launch {
            combine(
                args.filterNotNull(),
                confRepo.conf,
                newsApiSync.state,
                entriesRepo.selectCount(),
            ) { filter, conf, syncState, _ -> updateState(filter, conf, syncState) }.collectLatest { }
        }
    }

    fun hasBackend() = confRepo.conf.value.backend.isNotBlank()

    private suspend fun updateState(filter: EntriesFilter, conf: Conf, syncState: Sync.State) {
        if (!conf.initial_sync_completed || (conf.sync_on_startup && !conf.synced_on_startup)) {
            confRepo.update { it.copy(synced_on_startup = true) }
            viewModelScope.launch { newsApiSync.run() }
        }

        when (syncState) {
            is Sync.State.InitialSync -> _state.update { State.InitialSync(syncState.message) }

            else -> {
                val showBgProgress = when (syncState) {
                    is Sync.State.FollowUpSync -> syncState.args.syncEntries
                    else -> false
                }

                val scrollToTop = scrollToTopNextTime
                scrollToTopNextTime = false

                val rows: List<EntriesAdapterRow> = if (filter is EntriesFilter.BelongToFeed) {
                    entriesRepo.selectByFeedIdAndReadAndBookmarked(
                        feedId = filter.feedId,
                        read = if (conf.show_read_entries) listOf(true, false) else listOf(false),
                        bookmarked = false,
                    ).first()
                } else {
                    val includeRead = (conf.show_read_entries || filter is EntriesFilter.Bookmarked)
                    val includeBookmarked = filter is EntriesFilter.Bookmarked

                    entriesRepo.selectByReadAndBookmarked(
                        read = if (includeRead) listOf(true, false) else listOf(false),
                        bookmarked = includeBookmarked,
                    ).first()
                }

                val sortedRows = when (conf.sort_order) {
                    SORT_ORDER_ASCENDING -> rows.sortedBy { it.published }
                    SORT_ORDER_DESCENDING -> rows.sortedByDescending { it.published }
                    else -> throw Exception()
                }

                _state.update {
                    State.ShowingCachedEntries(
                        feed = if (filter is EntriesFilter.BelongToFeed) {
                            feedsRepo.selectById(filter.feedId)
                                .first()
                        } else {
                            null
                        },
                        entries = sortedRows.map { it.toItem(conf) },
                        showBackgroundProgress = showBgProgress,
                        scrollToTop = scrollToTop,
                        conf = conf,
                    )
                }
            }
        }
    }

    fun onRetry() {
        viewModelScope.launch { newsApiSync.run() }
    }

    fun onPullRefresh() {
        viewModelScope.launch { newsApiSync.run() }
    }

    fun saveConf(newConf: (Conf) -> Conf) {
        this.confRepo.update(newConf)
    }

    fun changeSortOrder() {
        scrollToTopNextTime = true

        confRepo.update {
            val newSortOrder = when (it.sort_order) {
                SORT_ORDER_ASCENDING -> SORT_ORDER_DESCENDING
                SORT_ORDER_DESCENDING -> SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            it.copy(sort_order = newSortOrder)
        }
    }

    fun setRead(entryIds: Collection<String>, read: Boolean) {
        viewModelScope.launch {
            entryIds.forEach {
                entriesRepo.updateReadAndReadSynced(
                    id = it,
                    read = read,
                    readSynced = false,
                )
            }

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
            entriesRepo.updateBookmarkedAndBookmaredSynced(
                id = entryId,
                bookmarked = bookmarked,
                bookmarkedSynced = false
            )

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
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

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun EntriesAdapterRow.toItem(conf: Conf): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = id,
            showImage = ext_show_preview_images ?: conf.show_preview_images,
            cropImage = conf.crop_preview_images,
            imageUrl = ext_og_image_url,
            imageWidth = ext_og_image_width.toInt(),
            imageHeight = ext_og_image_height.toInt(),
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = ext_read,
            openInBrowser = ext_open_entries_in_browser ?: false,
            useBuiltInBrowser = conf.use_built_in_browser,
            links = links,
        )
    }

    sealed class State {

        data class InitialSync(val message: String) : State()

        object LoadingCachedEntries : State()

        data class ShowingCachedEntries(
            val feed: Feed?,
            val entries: List<EntriesAdapter.Item>,
            val showBackgroundProgress: Boolean,
            val scrollToTop: Boolean = false,
            val conf: Conf,
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