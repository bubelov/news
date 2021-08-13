package log

import db.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.LocalDateTime
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class LogTree(
    private val log: LogRepository,
) : Timber.Tree() {

    @DelicateCoroutinesApi
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        GlobalScope.launch { logAsync(tag, message, t) }
    }

    private suspend fun logAsync(
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        val stackTrace = if (t == null) {
            null
        } else {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            sw.toString()
        }

        log.insert(
            Log(
                id = UUID.randomUUID().toString(),
                date = LocalDateTime.now().toString(),
                tag = tag ?: "untagged",
                message = message.lines().first(),
                stackTrace = stackTrace,
            )
        )
    }
}