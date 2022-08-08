package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import db.Conf
import db.Entry
import db.Feed
import entries.EntriesAdapterItem
import entries.EntriesFilter
import entries.EntriesRepo
import feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.Sync
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class SearchModel(
    confRepo: ConfRepo,
    private val entriesRepo: EntriesRepo,
    private val feedsRepo: FeedsRepo,
    private val sync: Sync,
) : ViewModel() {

    private val args = MutableStateFlow<Args?>(null)

    private val _state = MutableStateFlow<State>(State.QueryIsEmpty)
    val state = _state.asStateFlow()

    init {
        combine(
            args.filterNotNull(),
            confRepo.conf,
            entriesRepo.selectCount(),
        ) { args, conf, _ ->
            if (args.query.length < 3) {
                _state.update { State.QueryIsTooShort }
                return@combine
            }

            _state.update { State.RunningQuery }

            val entries = when (args.filter) {
                EntriesFilter.NotBookmarked -> entriesRepo.selectByQuery(args.query)
                EntriesFilter.Bookmarked -> entriesRepo.selectByQueryAndBookmarked(args.query, true)
                is EntriesFilter.BelongToFeed -> entriesRepo.selectByQueryAndFeedId(args.query, args.filter.feedId)
            }

            val feeds = feedsRepo.selectAll().first()

            val results = entries.first().map { entry ->
                val feed = feeds.single { feed -> feed.id == entry.feedId }
                entry.toRow(feed, conf)
            }

            _state.update { State.ShowingQueryResults(results) }
        }.launchIn(viewModelScope)
    }

    fun setArgs(args: Args) {
        this.args.update { args }
    }

    fun markAsRead(entryId: String) {
        viewModelScope.launch {
            entriesRepo.setRead(
                id = entryId,
                read = true,
                readSynced = false
            )

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
            id = id,
            showImage = false,
            cropImage = false,
            imageUrl = "",
            imageWidth = 0,
            imageHeight = 0,
            title = title,
            subtitle = "${feed.title} · ${DATE_TIME_FORMAT.format(published)}",
            summary = "",
            read = read,
            openInBrowser = feed.openEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    data class Args(
        val filter: EntriesFilter,
        val query: String,
    )

    sealed class State {
        object QueryIsEmpty : State()
        object QueryIsTooShort : State()
        object RunningQuery : State()
        data class ShowingQueryResults(val items: List<EntriesAdapterItem>) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}