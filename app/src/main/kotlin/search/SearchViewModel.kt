package search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.ConfRepository
import db.Conf
import db.Entry
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepository
import entries.EntriesSupportingTextRepository
import feeds.FeedsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import podcasts.PodcastsRepository
import sync.NewsApiSync
import sync.SyncResult
import timber.log.Timber

class SearchViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val podcastsRepository: PodcastsRepository,
    private val sync: NewsApiSync,
    private val conf: ConfRepository,
) : ViewModel() {

    val searchString = MutableStateFlow("")

    val searchResults = MutableStateFlow(listOf<EntriesAdapterItem>())

    val showProgress = MutableStateFlow(false)

    val showEmpty = MutableStateFlow(false)

    suspend fun onViewCreated(filter: EntriesFilter) {
        searchString.collectLatest { query ->
            searchResults.value = emptyList()
            showProgress.value = false
            showEmpty.value = false

            if (query.length < 3) {
                return@collectLatest
            }

            showProgress.value = true

            val entries = when (filter) {
                EntriesFilter.NotBookmarked -> {
                    delay(1500)
                    entriesRepository.selectByQuery(query)
                }
                EntriesFilter.Bookmarked -> entriesRepository.selectByQueryAndBookmarked(
                    query,
                    true,
                )
                is EntriesFilter.BelongToFeed -> entriesRepository.selectByQueryAndFeedId(
                    query,
                    filter.feedId,
                )
            }

            val feeds = feedsRepository.selectAll()
            val conf = this.conf.getAsFlow()

            val results = entries.map { entry ->
                val feed = feeds.singleOrNull { feed -> feed.id == entry.feedId }
                entry.toRow(feed, conf)
            }

            showProgress.value = false
            showEmpty.value = results.isEmpty()
            searchResults.value = results
        }
    }

    suspend fun getEntry(id: String) = entriesRepository.selectById(id)

    fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setRead(
        entryIds: Collection<String>,
        read: Boolean,
    ) {
        entryIds.forEach { entriesRepository.setRead(it, read) }

        viewModelScope.launch {
            val syncResult = sync.syncEntriesFlags()

            if (syncResult is SyncResult.Err) {
                Timber.e(syncResult.e)
            }
        }
    }

    suspend fun downloadPodcast(id: String) {
        podcastsRepository.download(id)
    }

    suspend fun reloadEntry(entry: EntriesAdapterItem) {
        searchResults.first().filter { it == entry }.forEach {
            it.read.value = entriesRepository.selectById(it.id)?.read ?: false
        }
    }

    fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = podcastsRepository.selectByEntryId(entryId) ?: return null

        val uri = runCatching {
            Uri.parse(enclosure.cacheUri)
        }.onFailure {
            Timber.e(it)
        }

        return uri.getOrNull()
    }

    private suspend fun Entry.toRow(feed: Feed?, conf: Flow<Conf>): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                (feed?.title ?: "Unknown feed") + " · " + published
            },
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = flow {
                podcastsRepository.getDownloadProgress(this@toRow.id).collect {
                    emit(it)
                }
            },
            image = flowOf(),
            cachedImage = lazy { null },
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
}