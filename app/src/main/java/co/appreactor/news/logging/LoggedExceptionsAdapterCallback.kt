package co.appreactor.news.logging

import co.appreactor.news.db.LoggedException

fun interface LoggedExceptionsAdapterCallback {
    fun onClick(item: LoggedException)
}