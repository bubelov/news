package app

import android.app.Application
import co.appreactor.news.BuildConfig
import conf.ConfRepository
import crash.CrashHandler
import db.db
import enclosures.AudioEnclosuresRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import opengraph.OpenGraphImagesRepository
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import sync.BackgroundSyncScheduler

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.DEBUG)
            androidContext(applicationContext)
            defaultModule()
            modules(module { single { db(applicationContext) } })
        }

        val confRepo = get<ConfRepository>()
        runBlocking { confRepo.save { it.copy(syncedOnStartup = false) } }

        val syncScheduler = get<BackgroundSyncScheduler>()
        GlobalScope.launch { syncScheduler.schedule(override = false) }

        val enclosuresRepo = get<AudioEnclosuresRepository>()
        GlobalScope.launch { enclosuresRepo.deletePartialDownloads() }

        val ogImagesRepo = get<OpenGraphImagesRepository>()
        GlobalScope.launch { ogImagesRepo.fetchEntryImages() }

        CrashHandler().setup(this)
    }
}