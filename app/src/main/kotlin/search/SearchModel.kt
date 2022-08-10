package search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import db.Conf
import db.SelectByIdIn
import entries.EntriesAdapterItem
import entries.EntriesRepo
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

            val entryIds = entriesRepo.selectByFtsQuery(args.query).first()
            val entries = entriesRepo.selectByIdIn(entryIds).first()
            val items = entries.map { it.toItem(conf) }

            _state.update { State.ShowingQueryResults(items) }
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

    private fun SelectByIdIn.toItem(conf: Conf): EntriesAdapterItem {
        return EntriesAdapterItem(
            id = id,
            showImage = showPreviewImages ?: conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = ogImageUrl,
            imageWidth = ogImageWidth.toInt(),
            imageHeight = ogImageHeight.toInt(),
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = read,
            openInBrowser = openEntriesInBrowser,
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
        data class ShowingQueryResults(val items: List<EntriesAdapterItem>) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}