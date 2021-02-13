package exceptions

import db.LoggedException

fun interface AppExceptionsAdapterCallback {
    fun onClick(item: LoggedException)
}