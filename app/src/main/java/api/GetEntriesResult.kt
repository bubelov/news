package api

import db.Entry

sealed class GetEntriesResult {
    data class Loading(val entriesLoaded: Long) : GetEntriesResult()
    data class Success(val entries: List<Entry>) : GetEntriesResult()
}