package co.appreactor.news.logging

import co.appreactor.news.db.LoggedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class PersistentLogTree(
    private val exceptionsRepository: LoggedExceptionsRepository
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null && t !is CancellationException) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            val stackTrace = sw.toString()

            runBlocking {
                exceptionsRepository.add(
                    LoggedException(
                        id = UUID.randomUUID().toString(),
                        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
                        exceptionClass = t.javaClass.simpleName,
                        message = t.message ?: "",
                        stackTrace = stackTrace
                    )
                )
            }
        }
    }
}