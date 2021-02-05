package entries

import androidx.lifecycle.ViewModel
import common.*
import db.EntryWithoutSummary
import feeds.FeedsRepository
import db.Feed
import common.Preferences.Companion.CROP_PREVIEW_IMAGES
import common.Preferences.Companion.INITIAL_SYNC_COMPLETED
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
import timber.log.Timber
import java.text.DateFormat
import java.util.*

class EntriesFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val podcastsRepository: PodcastsRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    lateinit var filter: EntriesFilter

    val loadingEntries = MutableStateFlow(false)

    val syncMessage = newsApiSync.syncMessage

    fun init(filter: EntriesFilter) {
        this.filter = filter
    }

    suspend fun getEntries(): Flow<List<EntriesAdapterItem>> {
        loadingEntries.value = true

        return combine(
            entriesRepository.getCount(),
            feedsRepository.getCount(),
            getEntriesPrefs(),
        ) { entriesCount, feedsCount, prefs ->
            loadingEntries.value = true
            Timber.d("Entries: $entriesCount, feeds: $feedsCount, prefs: $prefs")

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
                    entriesRepository.getAll().first().filter { it.feedId == filter.feedId }
                }
            }

            val sortedEntries = when (prefs.sortOrder) {
                SORT_ORDER_ASCENDING -> unsortedEntries.sortedBy { it.published }
                SORT_ORDER_DESCENDING -> unsortedEntries.sortedByDescending { it.published }
                else -> unsortedEntries
            }

            val feeds = feedsRepository.getAll().first()

            val result = sortedEntries.map {
                val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
                it.toRow(feed, prefs.showPreviewImages, prefs.cropPreviewImages)
            }

            loadingEntries.value = false
            result
        }
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

    suspend fun performInitialSyncIfNecessary() {
        if (!prefs.getBooleanBlocking(INITIAL_SYNC_COMPLETED)) {
            newsApiSync.performInitialSync()
        }
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.getBoolean(INITIAL_SYNC_COMPLETED)

    suspend fun getShowOpenedEntries() = prefs.getBoolean(SHOW_OPENED_ENTRIES)

    suspend fun setShowOpenedEntries(showOpenedEntries: Boolean) = prefs.putBoolean(
        SHOW_OPENED_ENTRIES, showOpenedEntries
    )

    suspend fun getSortOrder() = prefs.getString(SORT_ORDER)

    suspend fun setSortOrder(sortOrder: String) = prefs.putString(SORT_ORDER, sortOrder)

    suspend fun downloadPodcast(id: String) {
        podcastsRepository.download(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    suspend fun getFeed(id: String) = feedsRepository.get(id).first()

    suspend fun markAsOpened(entryId: String) {
        entriesRepository.setOpened(entryId, true)
        newsApiSync.syncEntriesFlags()
    }

    suspend fun markAsBookmarked(entryId: String) = withContext(Dispatchers.IO) {
        entriesRepository.setBookmarked(entryId, true)
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
            opened = opened,
        )
    }

    private data class EntriesSettings(
        val showOpenedEntries: Boolean,
        val sortOrder: String,
        val showPreviewImages: Boolean,
        val cropPreviewImages: Boolean,
    )
}