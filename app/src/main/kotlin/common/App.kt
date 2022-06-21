package common

import android.app.Application
import android.content.Intent
import co.appreactor.news.BuildConfig
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import db.database
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import sync.BackgroundSyncScheduler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.DEBUG)
            androidContext(this@App)
            defaultModule()
            modules(module { single { database(this@App) } })
        }

        val picasso = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)

        val syncScheduler = get<BackgroundSyncScheduler>()
        GlobalScope.launch { syncScheduler.schedule(override = false) }

        if (BuildConfig.DEBUG) {
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

                    startActivity(intent)
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
}