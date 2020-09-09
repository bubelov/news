package co.appreactor.nextcloud.news.logging

import co.appreactor.nextcloud.news.db.LoggedException

fun interface LoggedExceptionsAdapterCallback {
    fun onClick(item: LoggedException)
}