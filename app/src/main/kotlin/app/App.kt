package app

import android.app.Application
import co.appreactor.news.BuildConfig
import crash.CrashHandler
import db.db
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule

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
    }
}