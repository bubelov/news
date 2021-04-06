package common

import android.app.Application
import androidx.work.*
import co.appreactor.news.BuildConfig
import api.NewsApiSwitcher
import injections.appModule
import exceptions.AppExceptionsTree
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
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
            val authType = get<PreferencesRepository>().get().authType

            if (authType.isNotBlank()) {
                get<NewsApiSwitcher>().switch(authType)
            }
        }

        Timber.plant(AppExceptionsTree(get()))

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val picasso = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)

        setupBackgroundSync(override = false)
    }

    fun setupBackgroundSync(override: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val prefs = runBlocking { get<PreferencesRepository>().get() }

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
        private const val SYNC_WORK_NAME = "sync"
    }
}