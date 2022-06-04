package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.ConfRepository
import db.Entry
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepository
import feeds.FeedsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel
import sync.NewsApiSync

@KoinViewModel
class SearchViewModel(
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
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

    fun getConf() = confRepo.select()

    private fun Entry.toRow(feed: Feed?): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            ogImageUrl = "",
            ogImageWidth = 0,
            ogImageHeight = 0,
            cropImage = false,
            title = title,
            subtitle = (feed?.title ?: "Unknown feed") + " · " + published,
            summary = "",
            audioEnclosure = null,
            read = read,
        )
    }
}