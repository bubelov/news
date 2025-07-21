package sync

import api.miniflux.HackernewsApiBuilder
import api.miniflux.HnEntryJson
import db.HnEntry
import hnentries.HnEntriesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
class HnDownload(
    private val hnEntriesRepo: HnEntriesRepo,
) {

    sealed class State {
        object Idle : State()
        data class Updated(val MfEntryId: String, val gotKids: Boolean) : State()
        data class StartSync(val current: Long, val total: Int, val parentTitle: String) : State()
        data class InitialSync(val current: Long, val total: Int) : State()
        data class FailedToSync(val cause: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    private var current = 0L;

    private val api = HackernewsApiBuilder().build()

    suspend fun down(commentId: Long, mfEntryId: String){
        runCatching {
            current = 0;
            val parentEntry = parseEntry(api.getItem(commentId), mfEntryId)
            if (parentEntry != null){
                _state.update { State.StartSync(current, (parentEntry.descendants ?: 0).toInt(), parentEntry.title ?: "") }

                insertItemsIntoDB(parentEntry, mfEntryId,true)
                _state.update { State.Updated(mfEntryId, true) }
            }else{
                _state.update { State.FailedToSync("Failed to download. Parent is empty.") }
            }
        }.onFailure {
            _state.update { State.FailedToSync("Failed to sync comments. \n ${it}")}
        }
    }

    private fun parseEntry(hnEntryJson: HnEntryJson, mfEntryId: String): HnEntry?{
        if (hnEntryJson.deleted == null) {
            val d: List<Long> = hnEntryJson.kids ?: emptyList()
            val y: String = d.joinToString()
            return HnEntry(
                hnEntryJson.by,
                hnEntryJson.id,
                hnEntryJson.parent,
                hnEntryJson.time,
                hnEntryJson.type,
                hnEntryJson.text,
                hnEntryJson.dead ?: false,
                y,
                hnEntryJson.title,
                hnEntryJson.descendants,
                mfEntryId
            )
        }else{
            return null
        }
    }

    suspend fun insertItemsIntoDB(parentEntry: HnEntry, mfEntryId: String, getKids: Boolean){
        val kids: List<Long> = if(!parentEntry.kids.isNullOrEmpty()) parentEntry.kids.split(", ").map { it.toLong() } else emptyList()
        val dbEntries = mutableListOf(parentEntry)
        dbEntries += downloadKids(kids, mfEntryId,getKids)
        hnEntriesRepo.insertOrReplace(dbEntries)
    }

    suspend fun run(commentId: Long, mfEntryId: String, update: Boolean): SyncResult  {
        if (_state.value != State.Idle) {
            if (update){
                current = 0;
                _state.update { State.Idle }
            }else{
                return SyncResult.Failure(Exception("Already syncing"))
            }
        }
        runCatching {
            _state.update { State.InitialSync(current, 0) }
            if (!hnEntriesRepo.entryExists(commentId)) {
                val parentEntry = parseEntry(api.getItem(commentId), mfEntryId)
                if (parentEntry != null){
                    insertItemsIntoDB(parentEntry, mfEntryId, false)
                }
                else{
                    _state.update { State.FailedToSync("Failed to download. Parent is empty.") }
                }
            } else {
                val parentEntry = hnEntriesRepo.selectById(commentId).first()!!
                insertItemsIntoDB(parentEntry, mfEntryId,false)
            }
        }.onFailure {
            val msg = "Failed to sync comments. \n ${it}"
            _state.update { State.FailedToSync(msg) }
            return SyncResult.Failure(Exception(msg))
        }

        _state.update { State.Updated(mfEntryId, false) }
        return SyncResult.Success(0)
    }


    suspend private fun downloadKids(kids :List<Long>, mfEntryId: String, getKids : Boolean) : MutableList<HnEntry>  {
        val dbEntries = mutableListOf<HnEntry>()
        for (id in kids) {
            _state.update { State.InitialSync(current++, kids.size) }
            if (!hnEntriesRepo.entryExists(id) || getKids) {
                val parentEntry = parseEntry(api.getItem(id), mfEntryId)
                if (parentEntry != null)
                {
                    dbEntries.add(parentEntry)
                    val kidsList: List<Long> = if(!parentEntry.kids.isNullOrEmpty()) parentEntry.kids.split(", ").map { it.toLong() } else emptyList()
                    if (getKids && kidsList.isNotEmpty()) {
                        dbEntries += downloadKids(kidsList, mfEntryId,getKids)
                    }

                }
            }
        }
        return dbEntries
    }
}