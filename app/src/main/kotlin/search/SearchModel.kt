package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import db.Entry
import db.EntryWithoutContent
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepository
import feeds.FeedsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.NewsApiSync

@KoinViewModel
class SearchModel(
    private val feedsRepo: FeedsRepository,
    private val entriesRepo: EntriesRepository,
    private val confRepo: ConfRepo,
    private val sync: NewsApiSync,
) : ViewModel() {

    private val _filter = MutableStateFlow<EntriesFilter?>(null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Loaded("", emptyList()))
    val state = _state.asStateFlow()

    init {
        combine(_filter, _query) { filter, query ->
            if (filter == null) {
                _state.update { State.Loaded("", emptyList()) }
            } else {
                _state.update { State.Loaded(query, emptyList()) }

                if (query.length >= 3) {
                    _state.update { State.Loading }

                    val entries = when (filter) {
                        EntriesFilter.NotBookmarked -> {
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

                    _state.update { State.Loaded(query, results) }
                }
            }
        }.onEach {

        }.launchIn(viewModelScope)
    }

    fun setFilter(filter: EntriesFilter) {
        _filter.update { filter }
    }

    fun setQuery(query: String) {
        _query.update { query }
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

    fun loadConf() = confRepo.conf

    private fun Entry.toRow(feed: Feed?): EntriesAdapterItem {
        return EntriesAdapterItem(
            entry = EntryWithoutContent(
                links,
                summary,
                id,
                feedId,
                title,
                published,
                updated,
                authorName,
                read,
                readSynced,
                bookmarked,
                bookmarkedSynced,
                guidHash,
                commentsUrl,
                ogImageChecked,
                ogImageUrl,
                ogImageWidth,
                ogImageHeight
            ),
            showImage = false,
            cropImage = false,
            title = title,
            subtitle = (feed?.title ?: "Unknown feed") + " · " + published,
            summary = "",
            audioEnclosure = null,
            read = read,
        )
    }

    sealed class State {
        object Loading : State()
        data class Loaded(val query: String, val items: List<EntriesAdapterItem>) : State()
    }
}