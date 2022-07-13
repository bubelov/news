package app

import android.app.Application
import co.appreactor.news.BuildConfig
import conf.ConfRepo
import crash.CrashHandler
import db.db
import enclosures.EnclosuresRepo
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

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        CrashHandler().setup(this)

        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.DEBUG)
            androidContext(this@App)
            defaultModule()
            modules(module { single { db(this@App) } })
        }

        get<ConfRepo>().update { it.copy(syncedOnStartup = false) }
        get<BackgroundSyncScheduler>().schedule(override = false)

        val enclosuresRepo = get<EnclosuresRepo>()
        GlobalScope.launch { enclosuresRepo.deletePartialAudioDownloads() }
    }
}