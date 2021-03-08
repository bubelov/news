package entries

import androidx.lifecycle.ViewModel
import common.*
import db.EntryWithoutSummary
import feeds.FeedsRepository
import db.Feed
import common.Preferences.Companion.CROP_PREVIEW_IMAGES
import common.Preferences.Companion.INITIAL_SYNC_COMPLETED
import common.Preferences.Companion.MARK_SCROLLED_ENTRIES_AS_READ
import common.Preferences.Companion.SHOW_OPENED_ENTRIES
import common.Preferences.Companion.SHOW_PREVIEW_IMAGES
import common.Preferences.Companion.SORT_ORDER
import common.Preferences.Companion.SORT_ORDER_ASCENDING
import common.Preferences.Companion.SORT_ORDER_DESCENDING
import entriesimages.EntriesImagesRepository
import podcasts.PodcastsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class EntriesViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val podcastsRepository: PodcastsRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    lateinit var filter: EntriesFilter

    val state = MutableStateFlow<State>(State.Inactive)

    suspend fun onViewReady(filter: EntriesFilter) {
        this.filter = filter

        if (state.value is State.FailedToSync) {
            return
        }

        if (state.value == State.Inactive) {
            if (prefs.getBoolean(INITIAL_SYNC_COMPLETED).first()) {
                state.value = State.LoadingEntries
            } else {
                runCatching {
                    state.value = State.PerformingInitialSync(newsApiSync.syncMessage)
                    newsApiSync.performInitialSync()
                }.onFailure {
                    state.value = State.FailedToSync(it)
                    return
                }
            }
        }

        getEntriesPrefs().collect { prefs ->
            reloadEntries(prefs)
        }
    }

    suspend fun onRetry() {
        state.value = State.Inactive
        onViewReady(filter)
    }

    private suspend fun reloadEntries(prefs: EntriesSettings) {
        state.value = State.LoadingEntries

        val unsortedEntries = when (val filter = filter) {
            is EntriesFilter.OnlyNotBookmarked -> {
                if (prefs.showOpenedEntries) {
                    entriesRepository.getAll().first()
                } else {
                    entriesRepository.getNotOpened().first()
                }.filterNot { it.bookmarked }
            }

            is EntriesFilter.OnlyBookmarked -> {
                entriesRepository.getBookmarked().first()
            }

            is EntriesFilter.OnlyFromFeed -> {
                val feedEntries = entriesRepository.selectByFeedId(filter.feedId).first()

                if (prefs.showOpenedEntries) {
                    feedEntries
                } else {
                    feedEntries.filter { !it.opened }
                }
            }
        }

        val sortedEntries = when (prefs.sortOrder) {
            SORT_ORDER_ASCENDING -> unsortedEntries.sortedBy { it.published }
            SORT_ORDER_DESCENDING -> unsortedEntries.sortedByDescending { it.published }
            else -> unsortedEntries
        }

        val feeds = feedsRepository.selectAll()

        val result = sortedEntries.map {
            val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
            it.toRow(feed, prefs.showPreviewImages, prefs.cropPreviewImages)
        }

        state.value = State.ShowingEntries(result, prefs.showOpenedEntries)
    }

    private suspend fun getEntriesPrefs(): Flow<EntriesSettings> {
        return combine(
            flow = prefs.getBoolean(SHOW_OPENED_ENTRIES),
            flow2 = prefs.getString(SORT_ORDER),
            flow3 = prefs.getBoolean(SHOW_PREVIEW_IMAGES),
            flow4 = prefs.getBoolean(CROP_PREVIEW_IMAGES),
        ) { showOpenedEntries, sortOrder, showPreviewImages, cropPreviewImages ->
            EntriesSettings(showOpenedEntries, sortOrder, showPreviewImages, cropPreviewImages)
        }.distinctUntilChanged()
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
        reloadEntries(getEntriesPrefs().first())
    }

    suspend fun getShowOpenedEntries() = prefs.getBoolean(SHOW_OPENED_ENTRIES)

    suspend fun setShowOpenedEntries(showOpenedEntries: Boolean) = prefs.putBoolean(
        SHOW_OPENED_ENTRIES, showOpenedEntries
    )

    suspend fun getSortOrder() = prefs.getString(SORT_ORDER)

    suspend fun setSortOrder(sortOrder: String) = prefs.putString(SORT_ORDER, sortOrder)

    suspend fun getMarkScrolledEntriesAsRead(): Boolean {
        return prefs.getBoolean(MARK_SCROLLED_ENTRIES_AS_READ).first()
    }

    suspend fun downloadPodcast(id: String) {
        podcastsRepository.download(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    suspend fun getCachedEnclosureUri(entryId: String) =
        podcastsRepository.getCachedPodcastUri(entryId)

    suspend fun getFeed(id: String) = feedsRepository.selectById(id)

    suspend fun markAsOpened(entryId: String, changeState: Boolean = true) {
        if (changeState) {
            val state = state.value as State.ShowingEntries
            this.state.value =
                State.ShowingEntries(
                    state.entries.filterNot { it.id == entryId },
                    state.includesUnread
                )
        }

        entriesRepository.setOpened(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    fun markAsOpened(entriesIds: List<String>) {
        GlobalScope.launch {
            entriesIds.forEach { entriesRepository.setOpened(it, true) }
            newsApiSync.syncEntriesFlags()
        }
    }

    suspend fun markAsNotOpened(entryId: String) {
        entriesRepository.setOpened(entryId, false)
        reloadEntries(getEntriesPrefs().first())
        newsApiSync.syncEntriesFlags()
    }

    suspend fun markAsBookmarked(entryId: String) {
        val state = state.value as State.ShowingEntries
        this.state.value =
            State.ShowingEntries(state.entries.filterNot { it.id == entryId }, state.includesUnread)
        entriesRepository.setBookmarked(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    suspend fun markAsNotBookmarked(entryId: String) = withContext(Dispatchers.IO) {
        entriesRepository.setBookmarked(entryId, false)
        reloadEntries(getEntriesPrefs().first())
        newsApiSync.syncEntriesFlags()
    }

    private suspend fun EntryWithoutSummary.toRow(
        feed: Feed?,
        showFeedImages: Boolean,
        cropFeedImages: Boolean,
    ): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                val instant = Instant.parse(published)
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                (feed?.title ?: "Unknown feed") + " · " + format.format(Date(instant.millis))
            },
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = flow {
                podcastsRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flow {
                entriesImagesRepository.getPreviewImage(this@toRow.id).collect {
                    emit(it)
                }
            },
            cachedImage = lazy {
                runBlocking {
                    entriesImagesRepository.getPreviewImage(this@toRow.id).first()
                }

            },
            showImage = showFeedImages,
            cropImage = cropFeedImages,
            supportingText = flow {
                emit(
                    entriesSupportingTextRepository.getSupportingText(
                        this@toRow.id,
                        feed
                    )
                )
            },
            cachedSupportingText = entriesSupportingTextRepository.getCachedSupportingText(this.id),
            opened = MutableStateFlow(opened),
        )
    }

    private data class EntriesSettings(
        val showOpenedEntries: Boolean,
        val sortOrder: String,
        val showPreviewImages: Boolean,
        val cropPreviewImages: Boolean,
    )

    sealed class State {

        object Inactive : State()

        data class PerformingInitialSync(val message: Flow<String>) : State()

        data class FailedToSync(val error: Throwable) : State()

        object LoadingEntries : State()

        data class ShowingEntries(
            val entries: List<EntriesAdapterItem>,
            val includesUnread: Boolean,
        ) : State()
    }
}