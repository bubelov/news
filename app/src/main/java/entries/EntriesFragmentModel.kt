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
import entriesenclosures.EntriesEnclosuresRepository
import entriesenclosures.isAudioMime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class EntriesFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val entriesImagesRepository: EntriesImagesRepository,
    private val entriesEnclosuresRepository: EntriesEnclosuresRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    val syncMessage = newsApiSync.syncMessage

    suspend fun getEntries(): Flow<List<EntriesAdapterItem>> {
        return combine(
            entriesRepository.getCount(),
            feedsRepository.getCount(),
            prefs.getCount(),
        ) { _, _, _ ->
            val showOpenedEntries = prefs.getBoolean(SHOW_OPENED_ENTRIES).first()

            val unsortedEntries = if (showOpenedEntries) {
                entriesRepository.getAll().first()
            } else {
                entriesRepository.getNotOpened().first()
            }.filterNot { it.bookmarked }

            val sortedEntries = when (prefs.getString(SORT_ORDER).first()) {
                SORT_ORDER_ASCENDING -> unsortedEntries.sortedBy { it.published }
                SORT_ORDER_DESCENDING -> unsortedEntries.sortedByDescending { it.published }
                else -> unsortedEntries
            }

            val feeds = feedsRepository.getAll().first()
            val showPreviewImages = prefs.getBoolean(SHOW_PREVIEW_IMAGES).first()
            val cropPreviewImages = prefs.getBoolean(CROP_PREVIEW_IMAGES).first()

            val result = sortedEntries.map {
                val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
                it.toRow(feed, showPreviewImages, cropPreviewImages)
            }

            result
        }
    }

    suspend fun performInitialSyncIfNecessary() {
        if (!prefs.getBooleanBlocking(INITIAL_SYNC_COMPLETED)) {
            newsApiSync.performInitialSync()
        }
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    fun isInitialSyncCompleted() = prefs.getBooleanBlocking(INITIAL_SYNC_COMPLETED)

    suspend fun getShowOpenedEntries() = prefs.getBoolean(SHOW_OPENED_ENTRIES)

    suspend fun setShowOpenedEntries(showOpenedEntries: Boolean) = prefs.putBoolean(
        SHOW_OPENED_ENTRIES, showOpenedEntries
    )

    suspend fun getSortOrder() = prefs.getString(SORT_ORDER)

    suspend fun setSortOrder(sortOrder: String) = prefs.putString(SORT_ORDER, sortOrder)

    suspend fun downloadEnclosure(id: String) {
        entriesEnclosuresRepository.downloadEnclosure(id)
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

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
            podcast = enclosureLinkType.isAudioMime(),
            podcastDownloadPercent = flow {
                entriesEnclosuresRepository.getDownloadProgress(this@toRow.id).collect {
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
}