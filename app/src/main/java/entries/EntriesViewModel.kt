package entries

import androidx.lifecycle.ViewModel
import common.*
import db.EntryWithoutSummary
import feeds.FeedsRepository
import db.Feed
import common.PreferencesRepository.Companion.SORT_ORDER_ASCENDING
import common.PreferencesRepository.Companion.SORT_ORDER_DESCENDING
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
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private lateinit var filter: EntriesFilter

    val state = MutableStateFlow<State>(State.Inactive)

    val openedEntry = MutableStateFlow<EntriesAdapterItem?>(null)

    suspend fun onViewReady(filter: EntriesFilter) {
        this.filter = filter

        if (state.value is State.FailedToSync) {
            return
        }

        if (state.value == State.Inactive) {
            if (getPreferences().initialSyncCompleted) {
                reloadEntries()
            } else {
                runCatching {
                    state.value = State.PerformingInitialSync(newsApiSync.syncMessage)
                    newsApiSync.performInitialSync()
                    reloadEntries()
                }.onFailure {
                    state.value = State.FailedToSync(it)
                    return
                }
            }
        }
    }

    suspend fun onRetry() {
        state.value = State.Inactive
        onViewReady(filter)
    }

    suspend fun reloadEntry(entry: EntriesAdapterItem) {
        val freshEntry = entriesRepository.get(entry.id).first() ?: return
        entry.opened.value = freshEntry.opened

        val currentState = state.value

        if (currentState is State.ShowingEntries) {
            val hideEntry = fun() {
                state.value = currentState.copy(
                    entries = currentState.entries.toMutableList().apply {
                        remove(entry)
                    }
                )
            }

            if (freshEntry.opened && !currentState.includesUnread) {
                hideEntry()
            }

            if (freshEntry.bookmarked && filter is EntriesFilter.OnlyNotBookmarked) {
                hideEntry()
            }

            if (!freshEntry.bookmarked && filter is EntriesFilter.OnlyBookmarked) {
                hideEntry()
            }
        }
    }

    private suspend fun reloadEntries() {
        state.value = State.LoadingEntries
        val prefs = getPreferences()

        val unsortedEntries = when (val filter = filter) {
            is EntriesFilter.OnlyNotBookmarked -> {
                if (prefs.showOpenedEntries) {
                    entriesRepository.selectAll().first()
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

        state.value = State.ShowingEntries(
            result,
            prefs.showOpenedEntries || filter is EntriesFilter.OnlyBookmarked,
        )
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
        reloadEntries()
    }

    suspend fun getPreferences() = preferencesRepository.get()

    suspend fun savePreferences(action: Preferences.() -> Unit) {
        preferencesRepository.save(action)
        reloadEntries()
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
        reloadEntries()
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
        reloadEntries()
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