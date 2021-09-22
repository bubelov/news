package log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime

class LogTree(
    private val log: LogRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
) : Timber.Tree() {

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        scope.launch {
            val stackTrace = if (t == null) {
                ""
            } else {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                t.printStackTrace(pw)
                sw.toString()
            }

            log.insert(
                date = OffsetDateTime.now().toString(),
                level = priority.toLong(),
                tag = tag ?: "",
                message = message.lines().first(),
                stackTrace = stackTrace,
            )
        }
    }
}