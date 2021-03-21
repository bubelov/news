package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import common.NewsApiSync
import db.Entry
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepository
import entries.EntriesSupportingTextRepository
import feeds.FeedsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class SearchViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val entriesSupportingTextRepository: EntriesSupportingTextRepository,
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
                EntriesFilter.OnlyNotBookmarked -> {
                    delay(1500)
                    entriesRepository.selectByQuery(query)
                }
                EntriesFilter.OnlyBookmarked -> entriesRepository.selectByQueryAndBookmarked(
                    query,
                    true,
                )
                is EntriesFilter.OnlyFromFeed -> entriesRepository.selectByQueryAndFeedid(
                    query,
                    filter.feedId,
                )
            }

            val feeds = feedsRepository.selectAll()

            val results = entries.map { entry ->
                val feed = feeds.singleOrNull { feed -> feed.id == entry.feedId }
                entry.toRow(feed)
            }

            showProgress.value = false
            showEmpty.value = results.isEmpty()
            searchResults.value = results
        }
    }

    suspend fun getEntry(id: String) = entriesRepository.get(id).first()

    suspend fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setRead(entryId: String) {
        entriesRepository.setOpened(entryId, true)
        viewModelScope.launch { sync.syncEntriesFlags() }
    }

    private suspend fun Entry.toRow(feed: Feed?): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            title = title,
            subtitle = lazy {
                val instant = Instant.parse(published)
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                (feed?.title ?: "Unknown feed") + " · " + format.format(Date(instant.millis))
            },
            podcast = false,
            podcastDownloadPercent = flowOf(),
            image = flowOf(),
            cachedImage = lazy { null },
            showImage = false,
            cropImage = false,
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
}