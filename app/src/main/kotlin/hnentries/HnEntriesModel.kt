package hnentries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import conf.ConfRepo
import conf.ConfRepo.Companion.SORT_ORDER_ASCENDING
import conf.ConfRepo.Companion.SORT_ORDER_DESCENDING
import db.Conf
import db.HnEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.HnDownload
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class HnEntriesModel(
    private val confRepo: ConfRepo,
    private val hnEntriesRepo: HnEntriesRepo,
    private val hnDownload: HnDownload,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.LoadingCachedEntries)
    val state = _state.asStateFlow()
    var update = true

    private var scrollToTopNextTime = false

    init {
        viewModelScope.launch {
            combine(
                confRepo.conf,
                hnDownload.state,
            ) { conf, syncState -> updateState(conf, syncState) }.collectLatest { }
        }
        viewModelScope.launch {
            hnDownload.run(confRepo.conf.value.current_hn_id, confRepo.conf.value.mf_entry_id, update)
        }
    }

    private suspend fun updateState(conf: Conf, syncState: HnDownload.State) {
        when (syncState) {
            is HnDownload.State.InitialSync -> _state.update { State.InitialSync(syncState.current, syncState.total) }

            is HnDownload.State.Updated -> {
                val scrollToTop = scrollToTopNextTime
                scrollToTopNextTime = false

                val dbEntries = mutableListOf<HnEntry>()
                val entry : HnEntry = hnEntriesRepo.selectById(conf.current_hn_id).first()!!
                val kids : List<Long> = if (entry.kids != null) entry.kids.split(", ").map { it.toLong() } else emptyList()
                dbEntries.add(entry)
                for (entry in kids){
                    if (hnEntriesRepo.selectById(entry).first() != null)
                    {
                        dbEntries.add(hnEntriesRepo.selectById(entry).first()!!)
                    }
                    else{
                        _state.update { State.FailedToSync("Failed to find downloaded hn items in DB.") }
                    }
                }

                _state.update {
                    State.ShowingCachedEntries(
                        //entries = sortedRows.map { it.toItem(conf) },
                        entries = dbEntries.map { it.toItem(conf) },
                        scrollToTop = scrollToTop,
                        conf = conf,
                    )
                }

            }
            is HnDownload.State.FailedToSync -> {
                _state.update { State.FailedToSync(syncState.cause) }

            }

            else -> {}
        }
    }

    fun onRetry() {
        viewModelScope.launch { hnDownload.run(confRepo.conf.value.current_hn_id, confRepo.conf.value.mf_entry_id, update)}
    }

    fun onPullRefresh() {
        //viewModelScope.launch { hnDownload.run() }
    }

    fun saveConf(newConf: (Conf) -> Conf) {
        this.confRepo.update(newConf)
    }

    fun changeSortOrder() {
        scrollToTopNextTime = true

        confRepo.update {
            val newSortOrder = when (it.sort_order) {
                SORT_ORDER_ASCENDING -> SORT_ORDER_DESCENDING
                SORT_ORDER_DESCENDING -> SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            it.copy(sort_order = newSortOrder)
        }
    }

    private fun HnEntry.toItem(conf: Conf): HnEntriesAdapter.Item {

        var kidsCount = 0
        if (descendants != null)
        {
            kidsCount = descendants.toInt()
        }else{
            kidsCount = if(!kids.isNullOrEmpty()) kids.split(", ").map { it.toLong() }.size else 0
        }

        if (id != conf.current_hn_id || parent == 0L){
            var final = ""
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty())
            {
                final = "$title <br> $text"
            }else if(!title.isNullOrEmpty()){
                final = "$title"

            }else if(!text.isNullOrEmpty()){
                final = "$text"
            }

            return HnEntriesAdapter.Item(
                id =  id,
                time = time,
                title = title ?: "",
                by = by_,
                text = final,
                kidsCount = kidsCount
            )
        }else{
            return HnEntriesAdapter.Item(
                id =  parent,
                time = time,
                title = title ?: "",
                by = "",
                text = "Go back",
                kidsCount = kidsCount
            )

        }
    }

    //TWICE?
    sealed class State {

        data class InitialSync(val current: Long, val total: Int) : State()

        object LoadingCachedEntries : State()

        data class ShowingCachedEntries(
            val entries: List<HnEntriesAdapter.Item>,
            val scrollToTop: Boolean = false,
            val conf: Conf,
        ) : State()

        data class FailedToSync(val cause: String) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}