package co.appreactor.nextcloud.news.common

import android.app.Application
import co.appreactor.nextcloud.news.BuildConfig
import co.appreactor.nextcloud.news.di.apiModule
import co.appreactor.nextcloud.news.di.appModule
import co.appreactor.nextcloud.news.logging.PersistentLogTree
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import java.io.File

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(listOf(appModule, apiModule))
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.plant(PersistentLogTree(get()))

        val picasso = Picasso.Builder(get())
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)
    }
}