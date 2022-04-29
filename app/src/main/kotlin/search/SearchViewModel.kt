package search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.ConfRepository
import db.Entry
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepository
import entries.EntriesSupportingTextRepository
import feeds.FeedsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import enclosures.EnclosuresRepository
import kotlinx.coroutines.flow.first
import sync.NewsApiSync
import timber.log.Timber

class SearchViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
    private val enclosuresRepository: EnclosuresRepository,
    private val sync: NewsApiSync,
    private val confRepo: ConfRepository,
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

            val feeds = feedsRepository.selectAll().first()

            val results = entries.map { entry ->
                val feed = feeds.singleOrNull { feed -> feed.id == entry.feedId }
                entry.toRow(feed)
            }

            showProgress.value = false
            showEmpty.value = results.isEmpty()
            searchResults.value = results
        }
    }

    suspend fun getEntry(id: String) = entriesRepository.selectById(id)

    suspend fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setRead(
        entryIds: Collection<String>,
        read: Boolean,
    ) {
        entryIds.forEach { entriesRepository.setRead(it, read) }

        viewModelScope.launch {
            sync.sync(NewsApiSync.SyncArgs(
                syncFeeds = false,
                syncFlags = true,
                syncEntries = false,
            ))
        }
    }

    suspend fun downloadPodcast(id: String) {
        enclosuresRepository.download(id)
    }

    fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = enclosuresRepository.selectByEntryId(entryId) ?: return null

        val uri = runCatching {
            Uri.parse(enclosure.cacheUri)
        }.onFailure {
            Timber.e(it)
        }

        return uri.getOrNull()
    }

    fun getConf() = confRepo.select()

    private suspend fun Entry.toRow(feed: Feed?): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
            cropImage = false,
            title = title,
            subtitle = (feed?.title ?: "Unknown feed") + " · " + published,
            supportingText = entriesSupportingTextRepository.getSupportingText(this@toRow.id, feed),
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = enclosuresRepository.getDownloadProgress(this@toRow.id)
                .first(),
            read = read,
        )
    }
}