package co.appreactor.news.api

import co.appreactor.news.db.Entry

sealed class GetNotViewedEntriesResult {
    data class Loading(val entriesLoaded: Long) : GetNotViewedEntriesResult()
    data class Success(val entries: List<Entry>) : GetNotViewedEntriesResult()
}