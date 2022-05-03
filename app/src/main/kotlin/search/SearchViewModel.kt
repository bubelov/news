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
import entries.EntriesSummaryRepository
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
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
    private val entriesSummaryRepo: EntriesSummaryRepository,
    private val enclosuresRepo: EnclosuresRepository,
    private val confRepo: ConfRepository,
    private val sync: NewsApiSync,
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
                    entriesRepo.selectByQuery(query).first()
                }
                EntriesFilter.Bookmarked -> entriesRepo.selectByQueryAndBookmarked(
                    query,
                    true,
                ).first()
                is EntriesFilter.BelongToFeed -> entriesRepo.selectByQueryAndFeedId(
                    query,
                    filter.feedId,
                ).first()
            }

            val feeds = feedsRepo.selectAll().first()

            val results = entries.map { entry ->
                val feed = feeds.singleOrNull { feed -> feed.id == entry.feedId }
                entry.toRow(feed)
            }

            showProgress.value = false
            showEmpty.value = results.isEmpty()
            searchResults.value = results
        }
    }

    fun getEntry(id: String) = entriesRepo.selectById(id)

    fun getFeed(id: String) = feedsRepo.selectById(id)

    fun setRead(
        entryIds: Collection<String>,
        read: Boolean,
    ) {
        viewModelScope.launch {
            entryIds.forEach { entriesRepo.setRead(it, read, false) }

            sync.sync(
                NewsApiSync.SyncArgs(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    suspend fun downloadPodcast(id: String) {
        enclosuresRepo.download(id)
    }

    suspend fun getCachedPodcastUri(entryId: String): Uri? {
        val enclosure = enclosuresRepo.selectByEntryId(entryId).first() ?: return null

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
            summary = entriesSummaryRepo.getSummary(this@toRow.id, feed),
            podcast = enclosureLinkType.startsWith("audio"),
            podcastDownloadPercent = enclosuresRepo.getDownloadProgress(this@toRow.id)
                .first(),
            read = read,
        )
    }
}