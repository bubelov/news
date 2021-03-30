package entries

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import timber.log.Timber
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
    private val connectivityProbe: ConnectivityProbe,
) : ViewModel() {

    private lateinit var filter: EntriesFilter

    val state = MutableStateFlow<State>(State.Inactive)

    val openedEntry = MutableStateFlow<EntriesAdapterItem?>(null)

    suspend fun onViewReady(filter: EntriesFilter, sharedModel: EntriesSharedViewModel) {
        this.filter = filter

        if (state.value is State.FailedToSync) {
            return
        }

        if (state.value == State.Inactive) {
            val prefs = getPreferences()

            if (prefs.initialSyncCompleted) {
                reloadEntries()

                if (filter is EntriesFilter.OnlyNotBookmarked
                    && prefs.syncOnStartup
                    && !sharedModel.syncedOnStartup
                ) {
                    sharedModel.syncedOnStartup = true

                    if (connectivityProbe.online) {
                        fetchEntriesFromApi()
                    }
                }
            } else {
                sharedModel.syncedOnStartup = true

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

    suspend fun onRetry(sharedModel: EntriesSharedViewModel) {
        state.value = State.Inactive
        onViewReady(filter, sharedModel)
    }

    fun reloadEntry(entry: EntriesAdapterItem) {
        val freshEntry = entriesRepository.selectById(entry.id) ?: return
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

    private suspend fun reloadEntries(inBackground: Boolean = false) {
        if (inBackground) {
            state.value = when (val prevState = state.value) {
                is State.ShowingEntries -> prevState.copy(showBackgroundProgress = true)
                else -> State.LoadingEntries
            }
        } else {
            state.value = State.LoadingEntries
        }

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
                val feedEntries = entriesRepository.selectByFeedId(filter.feedId)

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
            entries = result,
            includesUnread = prefs.showOpenedEntries || filter is EntriesFilter.OnlyBookmarked,
            showBackgroundProgress = false,
        )
    }

    suspend fun fetchEntriesFromApi() {
        when (val prevState = state.value) {
            is State.ShowingEntries -> {
                state.value = prevState.copy(showBackgroundProgress = true)

                runCatching {
                    newsApiSync.sync()
                }.onSuccess {
                    reloadEntries(inBackground = true)
                }.onFailure {
                    state.value = prevState.copy()
                    throw it
                }
            }
        }
    }

    suspend fun getPreferences() = preferencesRepository.get()

    suspend fun savePreferences(action: Preferences.() -> Unit) {
        preferencesRepository.save(action)
        reloadEntries()
    }

    suspend fun downloadPodcast(id: String) {
        podcastsRepository.download(id)
    }

    fun getEntry(id: String) = entriesRepository.selectById(id)

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
        entryIds: Collection<String>,
        read: Boolean,
        scope: CoroutineScope = viewModelScope,
    ) {
        entryIds.forEach { entriesRepository.setOpened(it, read) }
        scope.launch { newsApiSync.syncEntriesFlags() }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        entriesRepository.setBookmarked(entryId, bookmarked)
        viewModelScope.launch { newsApiSync.syncEntriesFlags() }
    }

    fun show(entry: EntriesAdapterItem, entryIndex: Int) {
        when (val state = state.value) {
            is State.ShowingEntries -> {
                this.state.value = state.copy(
                    entries = state.entries.toMutableList().apply { add(entryIndex, entry) }
                )
            }
        }
    }

    fun hide(entry: EntriesAdapterItem) {
        when (val state = state.value) {
            is State.ShowingEntries -> {
                this.state.value = state.copy(
                    entries = state.entries.toMutableList().apply { removeAll { it == entry } }
                )
            }
        }
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
            val showBackgroundProgress: Boolean,
        ) : State()
    }
}