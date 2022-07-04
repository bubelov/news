package crash

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler {

    fun setup(context: Context) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))

            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_TEXT, sw.toString())
            }

            context.startActivity(intent)
        }
    }
}