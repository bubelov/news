package co.appreactor.nextcloud.news.common

import android.app.Application
import co.appreactor.nextcloud.news.BuildConfig
import co.appreactor.nextcloud.news.di.apiModule
import co.appreactor.nextcloud.news.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(listOf(appModule, apiModule))
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }
}