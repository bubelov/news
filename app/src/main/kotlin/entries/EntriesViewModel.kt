package entries

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.NetworkMonitor
import common.ConfRepository
import db.EntryWithoutSummary
import feeds.FeedsRepository
import db.Feed
import common.ConfRepository.Companion.SORT_ORDER_ASCENDING
import common.ConfRepository.Companion.SORT_ORDER_DESCENDING
import db.Conf
import entriesimages.EntriesImagesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import podcasts.PodcastsRepository
import sync.NewsApiSync
import sync.SyncResult
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntriesViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val podcastsRepository: PodcastsRepository,
    private val newsApiSync: NewsApiSync,
    private val conf: ConfRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private lateinit var filter: EntriesFilter

    val state = MutableStateFlow<State?>(null)

    suspend fun onViewCreated(filter: EntriesFilter, sharedModel: EntriesSharedViewModel) {
        this.filter = filter

        if (state.value == null) {
            val conf = getConf()

            if (conf.initialSyncCompleted) {
                if (filter is EntriesFilter.NotBookmarked
                    && conf.syncOnStartup
                    && !sharedModel.syncedOnStartup
                ) {
                    sharedModel.syncedOnStartup = true

                    if (networkMonitor.online) {
                        state.value = State.ShowingEntries(
                            entries = getCachedEntries(conf),
                            includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                            showBackgroundProgress = true,
                        )

                        fetchEntriesFromApi()
                    } else {
                        reloadEntries(inBackground = false)
                    }
                } else {
                    reloadEntries(inBackground = false)
                }
            } else {
                sharedModel.syncedOnStartup = true

                runCatching {
                    state.value = State.PerformingInitialSync(newsApiSync.syncMessage)
                    newsApiSync.performInitialSync()
                    reloadEntries()
                }.onFailure {
                    state.value = State.FailedToSync(it)
                }
            }
        } else {
            if (state.value is State.ShowingEntries) {
                runCatching {
                    reloadEntries(inBackground = true)
                }.onFailure {
                    state.value = State.FailedToSync(it)
                }
            }
        }
    }

    suspend fun onRetry(sharedModel: EntriesSharedViewModel) {
        state.value = null
        onViewCreated(filter, sharedModel)
    }

    suspend fun reloadEntry(entry: EntriesAdapterItem) {
        val freshEntry = entriesRepository.selectById(entry.id) ?: return
        entry.read.value = freshEntry.read

        var currentState: State? = null

        while (currentState !is State.ShowingEntries) {
            delay(100)
            currentState = state.value

            Timber.d(
                "Trying to reload entry (entry = %s, current_state = %s)",
                entry,
                currentState?.javaClass?.simpleName,
            )
        }

        val hideEntry = fun() {
            state.value = currentState.copy(
                entries = currentState.entries.toMutableList().apply {
                    remove(entry)
                }
            )
        }

        if (freshEntry.read && !currentState.includesUnread) {
            hideEntry()
        }

        if (freshEntry.bookmarked && filter is EntriesFilter.NotBookmarked) {
            hideEntry()
        }

        if (!freshEntry.bookmarked && filter is EntriesFilter.Bookmarked) {
            hideEntry()
        }
    }

    private suspend fun getCachedEntries(conf: Conf): List<EntriesAdapterItem> {
        val unsortedEntries = when (val filter = filter) {
            is EntriesFilter.NotBookmarked -> {
                if (conf.showReadEntries) {
                    entriesRepository.selectAll()
                } else {
                    entriesRepository.selectByRead(false)
                }.filterNot { it.bookmarked }
            }

            is EntriesFilter.Bookmarked -> {
                entriesRepository.getBookmarked().first()
            }

            is EntriesFilter.BelongToFeed -> {
                val feedEntries = entriesRepository.selectByFeedId(filter.feedId)

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

        val feeds = feedsRepository.selectAll()
        val confFlow = this.conf.getAsFlow()

        return sortedEntries.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, confFlow)
        }
    }

    private suspend fun reloadEntries(inBackground: Boolean = false) {
        when (val prevState = state.value) {
            is State.ShowingEntries -> {
                if (inBackground && !prevState.showBackgroundProgress) {
                    state.value = prevState.copy(showBackgroundProgress = true)
                }
            }
            else -> state.value = State.LoadingEntries
        }

        val conf = getConf()

        state.value = State.ShowingEntries(
            entries = getCachedEntries(conf),
            includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
            showBackgroundProgress = false,
        )
    }

    suspend fun fetchEntriesFromApi() {
        val prevState = state.value

        if (prevState !is State.ShowingEntries) {
            Timber.e("Tried to fetch new entries before loading cached entries")
            return
        }

        when (val res = newsApiSync.sync()) {
            is SyncResult.Ok -> {
                reloadEntries(inBackground = true)
            }
            is SyncResult.Err -> {
                state.value = prevState.copy(showBackgroundProgress = false)
                throw res.e
            }
        }
    }

    suspend fun getConf() = conf.get()

    suspend fun saveConf(conf: Conf) {
        this.conf.save(conf)
        reloadEntries()
    }

    suspend fun downloadPodcast(id: String) {
        podcastsRepository.download(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.selectById(id)

    fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = podcastsRepository.selectByEntryId(entryId) ?: return null

        val uri = runCatching {
            Uri.parse(enclosure.cacheUri)
        }.onFailure {
            Timber.e(it)
        }

        return uri.getOrNull()
    }

    fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setRead(
        items: Collection<EntriesAdapterItem>,
        read: Boolean,
    ) {
        items.forEach { entriesRepository.setRead(it.id, read) }

        val conf = runBlocking { conf.get() }

        if (!conf.showReadEntries) {
            items.forEach { hide(it) }
        }

        viewModelScope.launch {
            val syncResult = newsApiSync.syncEntriesFlags()

            if (syncResult is SyncResult.Err) {
                Timber.e(syncResult.e)
            }
        }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        entriesRepository.setBookmarked(entryId, bookmarked)

        viewModelScope.launch {
            val syncResult = newsApiSync.syncEntriesFlags()

            if (syncResult is SyncResult.Err) {
                Timber.e(syncResult.e)
            }
        }
    }

    fun show(entry: EntriesAdapterItem, entryIndex: Int) {
        val state = state.value

        if (state is State.ShowingEntries) {
            this.state.value = state.copy(
                entries = state.entries.toMutableList().apply { add(entryIndex, entry) }
            )
        }
    }

    fun hide(entry: EntriesAdapterItem) {
        val state = state.value

        if (state is State.ShowingEntries) {
            this.state.value = state.copy(
                entries = state.entries.toMutableList().apply { removeAll { it == entry } }
            )
        }
    }

    suspend fun markAllAsRead() {
        when (val filter = filter) {
            is EntriesFilter.NotBookmarked -> {
                entriesRepository.updateReadByBookmarked(
                    read = true,
                    bookmarked = false,
                )
            }

            is EntriesFilter.Bookmarked -> {
                entriesRepository.updateReadByBookmarked(
                    read = true,
                    bookmarked = true,
                )
            }

            is EntriesFilter.BelongToFeed -> {
                entriesRepository.updateReadByFeedId(
                    read = true,
                    feedId = filter.feedId,
                )
            }
        }

        reloadEntries(inBackground = true)

        viewModelScope.launch {
            val syncResult = newsApiSync.syncEntriesFlags()

            if (syncResult is SyncResult.Err) {
                Timber.e(syncResult.e)
            }
        }
    }

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        conf: Flow<Conf>,
    ): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                "${feed?.title ?: "Unknown feed"} · ${DATE_TIME_FORMAT.format(published)}"
            },
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = flow {
                podcastsRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flow {
                if (feed?.showPreviewImages == false) {
                    return@flow
                }

                entriesImagesRepository.getPreviewImage(this@toRow.id).collect {
                    emit(it)
                }
            },
            cachedImage = lazy {
                runBlocking {
                    if (feed?.showPreviewImages == false) {
                        null
                    } else {
                        entriesImagesRepository.getPreviewImage(this@toRow.id).first()
                    }
                }

            },
            supportingText = flow {
                emit(
                    entriesSupportingTextRepository.getSupportingText(
                        this@toRow.id,
                        feed
                    )
                )
            },
            cachedSupportingText = entriesSupportingTextRepository.getCachedSupportingText(this.id),
            read = MutableStateFlow(read),
            conf = conf,
        )
    }

    sealed class State {

        data class PerformingInitialSync(val message: Flow<String>) : State()

        data class FailedToSync(val error: Throwable) : State()

        object LoadingEntries : State()

        data class ShowingEntries(
            val entries: List<EntriesAdapterItem>,
            val includesUnread: Boolean,
            val showBackgroundProgress: Boolean,
        ) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}