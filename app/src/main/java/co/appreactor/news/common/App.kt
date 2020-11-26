package co.appreactor.news.common

import android.app.Application
import co.appreactor.news.BuildConfig
import co.appreactor.news.di.appModule
import co.appreactor.news.di.dbModule
import co.appreactor.news.entriesenclosures.EntriesEnclosuresRepository
import co.appreactor.news.entriesimages.EntriesImagesRepository
import co.appreactor.news.logging.PersistentLogTree
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import timber.log.Timber
import java.io.File

class App : Application() {

    companion object {
        const val DB_FILE_NAME = "news.db"
    }

    private var globalJob: Job? = null

    private var syncPreviewsJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val picasso = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)

        setUp(appModule, dbModule)
    }

    private fun setUp(vararg modules: Module) {
        globalJob?.cancel()

        stopKoin()

        startKoin {
            androidContext(this@App)
            modules(*modules)
        }

        Timber.uprootAll()

        Timber.plant(Timber.DebugTree(), PersistentLogTree(get()))

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        globalJob = GlobalScope.launch {
            launch {
                get<EntriesEnclosuresRepository>().apply {
                    deleteDownloadedEnclosuresWithoutFiles()
                    deletePartialDownloads()
                }
            }

            launch {
                get<Preferences>().showPreviewImages().collect { show ->
                    if (show) {
                        syncPreviews()
                    }
                }
            }
        }
    }

    private suspend fun syncPreviews() {
        syncPreviewsJob?.cancel()

        syncPreviewsJob = GlobalScope.launch {
            runCatching {
                get<EntriesImagesRepository>().syncPreviews()
            }.onFailure {
                if (it is CancellationException) {
                    Timber.d("Sync previews cancelled")
                }
            }
        }
    }
}