package common

import android.app.Application
import co.appreactor.news.BuildConfig
import api.NewsApiSwitcher
import di.appModule
import di.dbModule
import exceptions.AppExceptionsTree
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import common.Preferences.Companion.AUTH_TYPE
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import java.io.File

class App : Application() {

    companion object {
        const val DB_FILE_NAME = "news.db"
    }

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(appModule, dbModule)
        }

        runBlocking {
            val authType = get<Preferences>().getStringBlocking(AUTH_TYPE)

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
    }
}