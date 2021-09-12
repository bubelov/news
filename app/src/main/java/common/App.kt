package common

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import api.NewsApiSwitcher
import co.appreactor.news.BuildConfig
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import log.LogTree
import injections.appModule
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import sync.SyncWorker
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }

        runBlocking {
            val authType = get<ConfRepository>().get().authType

            if (authType.isNotBlank()) {
                get<NewsApiSwitcher>().switch(authType)
                setupBackgroundSync(override = false)
            }
        }

        Timber.plant(LogTree(get()))

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val picasso = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)
    }

    fun setupBackgroundSync(override: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val prefs = runBlocking { get<ConfRepository>().get() }

        if (!prefs.syncInBackground) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        if (prefs.syncInBackground) {
            val policy = if (override) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = prefs.backgroundSyncIntervalMillis,
                repeatIntervalTimeUnit = TimeUnit.MILLISECONDS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                policy,
                periodicSyncRequest,
            )
        }
    }

    companion object {
        const val DB_FILE_NAME = "news.db"

        private const val SYNC_WORK_NAME = "sync"
    }
}