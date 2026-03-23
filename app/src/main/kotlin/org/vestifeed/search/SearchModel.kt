package org.vestifeed.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.vestifeed.db.Conf
import org.vestifeed.db.SelectByQuery
import org.vestifeed.entries.EntriesAdapter
import org.vestifeed.entries.EntriesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.db.Db
import org.vestifeed.sync.Sync
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SearchModel(
    db: Db,
    private val entriesRepo: EntriesRepo,
    private val sync: Sync,
) : ViewModel() {

    private val args = MutableStateFlow<Args?>(null)

    private val _state = MutableStateFlow<State>(State.QueryIsEmpty)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            args.filterNotNull().collect { args ->
                val conf = db.confQueries.select()
                if (args.query.length < 3) {
                    _state.update { State.QueryIsTooShort }
                    return@collect
                }

                _state.update { State.RunningQuery }

                val rows = entriesRepo.selectByFtsQuery(args.query).first()
                val items = rows.map { it.toItem(conf) }

                _state.update { State.ShowingQueryResults(items) }
            }
        }
    }

    fun setArgs(args: Args) {
        this.args.update { args }
    }

    fun markAsRead(entryId: String) {
        viewModelScope.launch {
            entriesRepo.updateReadAndReadSynced(
                id = entryId,
                read = true,
                readSynced = false,
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

    private fun SelectByQuery.toItem(conf: Conf): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = id,
            showImage = extShowPreviewImages || conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = extOpenGraphImageUrl,
            imageWidth = extOpenGraphImageWidth,
            imageHeight = extOpenGraphImageHeight,
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = extRead,
            openInBrowser = extOpenEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    data class Args(
        val query: String,
    )

    sealed class State {
        object QueryIsEmpty : State()
        object QueryIsTooShort : State()
        object RunningQuery : State()
        data class ShowingQueryResults(val items: List<EntriesAdapter.Item>) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}