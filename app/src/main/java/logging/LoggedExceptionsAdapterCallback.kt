package logging

import db.LoggedException

fun interface LoggedExceptionsAdapterCallback {
    fun onClick(item: LoggedException)
}