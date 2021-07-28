package exceptions

import db.LogEntry
import db.LoggedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logentries.LogEntriesRepository
import org.joda.time.LocalDateTime
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class PersistentTree(
    private val exceptionLog: AppExceptionsRepository,
    private val log: LogEntriesRepository,
) : Timber.Tree() {

    @DelicateCoroutinesApi
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        GlobalScope.launch { logAsync(message, t) }
    }

    private suspend fun logAsync(
        message: String,
        t: Throwable?
    ) {
        if (t == null) {
            log.insert(logEntry(message))
        } else {
            if (t is CancellationException) return

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            val stackTrace = sw.toString()

            exceptionLog.insert(
                LoggedException(
                    id = UUID.randomUUID().toString(),
                    date = LocalDateTime.now().toString(),
                    exceptionClass = t.javaClass.simpleName,
                    message = message.lines().first(),
                    stackTrace = stackTrace,
                )
            )
        }
    }

    fun logEntry(message: String) = LogEntry(
        id = UUID.randomUUID().toString(),
        date = LocalDateTime.now().toString(),
        tag = this.javaClass.simpleName,
        message = message,
    )
}