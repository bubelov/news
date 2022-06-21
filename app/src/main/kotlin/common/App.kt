package common

import android.app.Application
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.appreactor.news.BuildConfig
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import db.database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import sync.SyncWorker
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
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

    fun setupBackgroundSync(override: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val conf = runBlocking { get<ConfRepository>().load().first() }

        if (!conf.syncInBackground) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        val policy = if (override) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = conf.backgroundSyncIntervalMillis,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS,
        )
            .setConstraints(constraints)
            .setInitialDelay(conf.backgroundSyncIntervalMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            policy,
            periodicSyncRequest,
        )
    }

    companion object {
        // When updating, should also update backup_rules.xml
        const val DB_FILE_NAME = "news-v3.db"

        private const val SYNC_WORK_NAME = "sync"
    }
}