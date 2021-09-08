package api

import db.Entry

sealed class GetEntriesResult {
    data class Loading(val entriesLoaded: Long, val currentBatch: List<Entry>) : GetEntriesResult()
    object Success : GetEntriesResult()
}