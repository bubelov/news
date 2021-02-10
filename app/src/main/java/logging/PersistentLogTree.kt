package logging

import db.LoggedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class PersistentLogTree(
    private val exceptionsRepository: LoggedExceptionsRepository
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null && t !is CancellationException) {
            t.printStackTrace()

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            val stackTrace = sw.toString()

            runBlocking {
                exceptionsRepository.add(
                    LoggedException(
                        id = UUID.randomUUID().toString(),
                        date = LocalDateTime.now().toString(),
                        exceptionClass = t.javaClass.simpleName,
                        message = t.message ?: "",
                        stackTrace = stackTrace
                    )
                )
            }
        }
    }
}