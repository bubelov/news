package common

import android.app.Application
import co.appreactor.news.BuildConfig
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import crash.CrashHandler
import db.database
import enclosures.AudioEnclosuresRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import opengraph.OpenGraphImagesRepository
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import sync.BackgroundSyncScheduler
import java.io.File

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

        val enclosuresRepo = get<AudioEnclosuresRepository>()
        GlobalScope.launch { enclosuresRepo.deleteIncompleteDownloads() }

        val ogImagesRepo = get<OpenGraphImagesRepository>()
        GlobalScope.launch { ogImagesRepo.fetchEntryImages() }

        CrashHandler().setup(this)
    }
}