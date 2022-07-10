package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import db.Conf
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.Sync

@KoinViewModel
class SearchModel(
    confRepo: ConfRepo,
    private val entriesRepo: EntriesRepository,
    private val feedsRepo: FeedsRepository,
    private val sync: Sync,
) : ViewModel() {

    private val _filter = MutableStateFlow<EntriesFilter?>(null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Loaded("", emptyList()))
    val state = _state.asStateFlow()

    init {
        combine(_filter, _query, confRepo.conf) { filter, query, conf ->
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
                        val feed = feeds.single { feed -> feed.id == entry.feedId }
                        entry.toRow(feed, conf)
                    }

                    _state.update { State.Loaded(query, results) }
                }
            }
        }.launchIn(viewModelScope)
    }

    fun setFilter(filter: EntriesFilter) {
        _filter.update { filter }
    }

    fun setQuery(query: String) {
        _query.update { query }
    }

    fun setRead(entryIds: Collection<String>, value: Boolean) {
        viewModelScope.launch {
            entryIds.forEach { entriesRepo.setRead(it, value, false) }

            sync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun Entry.toRow(feed: Feed, conf: Conf): EntriesAdapterItem {
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
            feed = feed,
            conf = conf,
            showImage = false,
            cropImage = false,
            title = title,
            subtitle = feed.title + " · " + published,
            summary = "",
            read = read,
        )
    }

    sealed class State {
        object Loading : State()
        data class Loaded(val query: String, val items: List<EntriesAdapterItem>) : State()
    }
}