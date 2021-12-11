package log

import db.Log

fun interface LogCallback {
    fun onClick(item: Log)
}