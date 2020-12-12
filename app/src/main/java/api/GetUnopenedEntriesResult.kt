package api

import co.appreactor.news.db.Entry

sealed class GetUnopenedEntriesResult {
    data class Loading(val entriesLoaded: Long) : GetUnopenedEntriesResult()
    data class Success(val entries: List<Entry>) : GetUnopenedEntriesResult()
}