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
import db.EntryImage
import entriesimages.EntriesImagesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import enclosures.EnclosuresRepository
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
    private val enclosuresRepository: EnclosuresRepository,
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
                        state.value = State.ShowingEntries(
                            entries = getCachedEntries(conf),
                            includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                            showBackgroundProgress = false,
                        )
                    }
                } else {
                    state.value = State.ShowingEntries(
                        entries = getCachedEntries(conf),
                        includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                        showBackgroundProgress = false,
                    )
                }
            } else {
                sharedModel.syncedOnStartup = true

                runCatching {
                    Timber.d("Changing state!!!")
                    state.value = State.PerformingInitialSync(newsApiSync.syncMessage)
                    newsApiSync.performInitialSync()

                    Timber.d("Changing state!!!")
                    state.value = State.ShowingEntries(
                        entries = getCachedEntries(conf),
                        includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                        showBackgroundProgress = false,
                    )
                }.onFailure {
                    Timber.d("Changing state!!!")
                    state.value = State.FailedToSync(it)
                }
            }
        } else {
            if (state.value is State.ShowingEntries) {
                runCatching {
                    val conf = getConf()

                    state.value = State.ShowingEntries(
                        entries = getCachedEntries(conf),
                        includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                        showBackgroundProgress = false,
                    )
                }.onFailure {
                    Timber.d("Changing state!!!")
                    state.value = State.FailedToSync(it)
                }
            }
        }
    }

    suspend fun onRetry(sharedModel: EntriesSharedViewModel) {
        state.value = null
        onViewCreated(filter, sharedModel)
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

        return sortedEntries.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, conf)
        }
    }

    suspend fun fetchEntriesFromApi() {
        when (val res = newsApiSync.sync()) {
            is SyncResult.Ok -> {
                if (state.value is State.ShowingEntries) {
                    val conf = getConf()

                    state.value = State.ShowingEntries(
                        entries = getCachedEntries(conf),
                        includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                        showBackgroundProgress = false,
                    )
                }
            }
            is SyncResult.Err -> {
                throw res.e
            }
        }
    }

    suspend fun getConf() = conf.get()

    suspend fun saveConf(conf: Conf) {
        this.conf.save(conf)

        if (state.value is State.ShowingEntries) {
            state.value = State.ShowingEntries(
                entries = getCachedEntries(conf),
                includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                showBackgroundProgress = false,
            )
        }
    }

    suspend fun downloadPodcast(id: String) {
        enclosuresRepository.download(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.selectById(id)

    fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = enclosuresRepository.selectByEntryId(entryId) ?: return null

        val uri = runCatching {
            Uri.parse(enclosure.cacheUri)
        }.onFailure {
            Timber.e(it)
        }

        return uri.getOrNull()
    }

    fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setRead(
        entryIds: Collection<String>,
        read: Boolean,
    ) {
        entryIds.forEach { entriesRepository.setRead(it, read) }

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

        if (state.value is State.ShowingEntries) {
            val conf = getConf()

            state.value = State.ShowingEntries(
                entries = getCachedEntries(conf),
                includesUnread = conf.showReadEntries || filter is EntriesFilter.Bookmarked,
                showBackgroundProgress = false,
            )
        }

        viewModelScope.launch {
            val syncResult = newsApiSync.syncEntriesFlags()

            if (syncResult is SyncResult.Err) {
                Timber.e(syncResult.e)
            }
        }
    }

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        conf: Conf,
    ): EntriesAdapterItem {
        val image: EntryImage? = if (conf.showPreviewImages) {
            entriesImagesRepository.getPreviewImage(this@toRow.id).first()
        } else {
            null
        }

        val supportingText = if (conf.showPreviewText) {
            entriesSupportingTextRepository.getSupportingText(this@toRow.id, feed)
        } else {
            ""
        }

        return EntriesAdapterItem(
            id = id,
            image = image,
            cropImage = conf.cropPreviewImages,
            title = title,
            subtitle = "${feed?.title ?: "Unknown feed"} · ${DATE_TIME_FORMAT.format(published)}",
            supportingText = supportingText,
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = enclosuresRepository.getDownloadProgress(this@toRow.id)
                .first(),
            read = read,
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