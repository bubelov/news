package entries

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.ConfRepository
import common.ConfRepository.Companion.SORT_ORDER_ASCENDING
import common.ConfRepository.Companion.SORT_ORDER_DESCENDING
import db.Conf
import db.EntryWithoutSummary
import db.Feed
import enclosures.EnclosuresRepository
import feeds.FeedsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sync.NewsApiSync
import sync.SyncResult
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntriesModel(
    private val confRepo: ConfRepository,
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
    private val newsApiSync: NewsApiSync,
    private val enclosuresRepo: EnclosuresRepository,
) : ViewModel() {

    val filter = MutableStateFlow<EntriesFilter?>(null)

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    private var syncedOnStartup = false

    private var scrollToTopNextTime = false

    init {
        filter.onEach { filter ->
            if (filter == null) {
                return@onEach
            }

            _state.update { State.LoadingCachedEntries }

            confRepo.select().first().apply {
                if (!initialSyncCompleted || (syncOnStartup && !syncedOnStartup)) {
                    syncedOnStartup = true
                    viewModelScope.launch { newsApiSync.sync() }
                }
            }

            combine(
                confRepo.select(),
                feedsRepo.selectAll(),
                entriesRepo.selectCount(),
                newsApiSync.state,
            ) { conf, feeds, _, syncState ->
                when (syncState) {
                    is NewsApiSync.State.InitialSync -> State.InitialSync(syncState.message)
                    else -> {
                        val showBgProgress = when (syncState) {
                            is NewsApiSync.State.Idle -> false
                            is NewsApiSync.State.InitialSync -> false
                            is NewsApiSync.State.FollowUpSync -> syncState.args.syncEntries
                        }

                        val scrollToTop = scrollToTopNextTime
                        scrollToTopNextTime = false

                        State.ShowingCachedEntries(
                            entries = selectEntries(filter, conf, feeds),
                            showBackgroundProgress = showBgProgress,
                            scrollToTop = scrollToTop,
                        )
                    }
                }
            }.collect { state -> _state.update { state } }
        }.launchIn(viewModelScope)
    }

    suspend fun onRetry() {
        viewModelScope.launch { newsApiSync.sync() }
    }

    private suspend fun selectEntries(
        filter: EntriesFilter,
        conf: Conf,
        feeds: List<Feed>
    ): List<EntriesAdapterItem> {
        val unsortedEntries = when (filter) {
            is EntriesFilter.NotBookmarked -> {
                if (conf.showReadEntries) {
                    entriesRepo.selectAll().first()
                } else {
                    entriesRepo.selectByRead(false)
                }.filterNot { it.bookmarked }
            }

            is EntriesFilter.Bookmarked -> {
                entriesRepo.getBookmarked().first()
            }

            is EntriesFilter.BelongToFeed -> {
                val feedEntries = entriesRepo.selectByFeedId(filter.feedId)

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

        return sortedEntries.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, conf)
        }
    }

    suspend fun onPullRefresh() {
        val syncResult = newsApiSync.sync()
        if (syncResult is SyncResult.Failure) throw syncResult.cause
    }

    fun getConf() = confRepo.select()

    suspend fun saveConf(conf: Conf) {
        this.confRepo.upsert(conf)
    }

    fun changeSortOrder() {
        viewModelScope.launch {
            val conf = confRepo.select().first()

            val newSortOrder = when (conf.sortOrder) {
                SORT_ORDER_ASCENDING -> SORT_ORDER_DESCENDING
                SORT_ORDER_DESCENDING -> SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            scrollToTopNextTime = true
            confRepo.upsert(conf.copy(sortOrder = newSortOrder))
        }
    }

    suspend fun downloadPodcast(id: String) {
        enclosuresRepo.download(id)
    }

    suspend fun getFeed(id: String) = feedsRepo.selectById(id)

    fun getEntry(id: String) = entriesRepo.selectById(id)

    suspend fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = enclosuresRepo.selectByEntryId(entryId).first() ?: return null

        val uri = runCatching {
            Uri.parse(enclosure.cacheUri)
        }

        return uri.getOrNull()
    }

    fun setRead(entryIds: Collection<String>, value: Boolean) {
        entryIds.forEach { entriesRepo.setRead(it, value) }
        viewModelScope.launch {
            newsApiSync.sync(
                NewsApiSync.SyncArgs(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        entriesRepo.setBookmarked(entryId, bookmarked)
        viewModelScope.launch {
            newsApiSync.sync(
                NewsApiSync.SyncArgs(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    suspend fun markAllAsRead() {
        when (val filter = filter.value) {
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
            newsApiSync.sync(
                NewsApiSync.SyncArgs(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun EntryWithoutSummary.toRow(
        feed: Feed?,
        conf: Conf,
    ): EntriesAdapterItem {
        val ogImageUrl = if (conf.showPreviewImages) {
            ogImageUrl
        } else {
            ""
        }

        return EntriesAdapterItem(
            id = id,
            ogImageUrl = ogImageUrl,
            ogImageWidth = ogImageWidth,
            ogImageHeight = ogImageHeight,
            cropImage = conf.cropPreviewImages,
            title = title,
            subtitle = "${feed?.title ?: "Unknown feed"} · ${DATE_TIME_FORMAT.format(published)}",
            supportingText = "",
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = null,
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