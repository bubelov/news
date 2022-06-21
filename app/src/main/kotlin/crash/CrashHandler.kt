package crash

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler {

    fun setup(context: Context) {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))

                val intent = Intent(Intent.ACTION_SEND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_TEXT, sw.toString())
                    type = "text/plain"
                }

                context.startActivity(intent)
            }.onFailure {
                it.printStackTrace()
            }

            if (oldHandler != null) {
                oldHandler.uncaughtException(t, e)
            } else {
                exitProcess(1)
            }
        }
    }
}