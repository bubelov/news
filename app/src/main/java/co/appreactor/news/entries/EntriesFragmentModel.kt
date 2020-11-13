package co.appreactor.news.entries

import androidx.lifecycle.ViewModel
import co.appreactor.news.common.*
import co.appreactor.news.db.EntryWithoutSummary
import co.appreactor.news.feeds.FeedsRepository
import co.appreactor.news.db.Feed
import co.appreactor.news.entriesimages.EntriesImagesRepository
import co.appreactor.news.entriesenclosures.EntriesEnclosuresRepository
import co.appreactor.news.entriesenclosures.isAudioMime
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
    private val entriesEnclosuresRepository: EntriesEnclosuresRepository,
    private val newsApiSync: NewsApiSync,
    private val prefs: Preferences,
) : ViewModel() {

    val syncMessage = newsApiSync.syncMessage

    suspend fun getEntries(): Flow<List<EntriesAdapterItem>> {
        val start = System.currentTimeMillis()
        var reported = false

        return combine(
            feedsRepository.getAll(),
            entriesRepository.getCount(),
            prefs.showOpenedEntries(),
            prefs.showPreviewImages(),
            prefs.cropPreviewImages(),
        ) { feeds, _, showOpenedEntries, showPreviewImages, cropPreviewImages ->
            val entries = if (showOpenedEntries) {
                entriesRepository.getAll().first()
            } else {
                entriesRepository.getNotOpened().first()
            }.filterNot { it.bookmarked }

            Timber.d("Got ${entries.size} results in ${System.currentTimeMillis() - start} ms")

            val result = entries.map {
                val feed = feeds.singleOrNull { feed -> feed.id == it.feedId }
                it.toRow(feed, showPreviewImages, cropPreviewImages)
            }

            if (!reported) {
                reported = true
                Timber.d("Prepared results in ${System.currentTimeMillis() - start} ms")
            }

            result
        }
    }

    suspend fun performInitialSyncIfNecessary() {
        if (!prefs.initialSyncCompleted().first()) {
            newsApiSync.performInitialSync()
        }
    }

    suspend fun performFullSync() {
        newsApiSync.sync()
    }

    suspend fun isInitialSyncCompleted() = prefs.initialSyncCompleted().first()

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
            supportingText = flow { emit(entriesSupportingTextRepository.getSupportingText(this@toRow.id, feed)) },
            cachedSupportingText = entriesSupportingTextRepository.getCachedSupportingText(this.id),
            opened = opened,
        )
    }
}