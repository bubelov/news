package org.vestifeed.app

import android.app.Application
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.sqlite.driver.AndroidSQLiteDriver
import org.vestifeed.api.Api
import org.vestifeed.api.HotSwapApi
import org.vestifeed.db.Db
import org.vestifeed.di.Di
import java.io.File

class App : Application() {

    val api by lazy { HotSwapApi(db) }

    val db by lazy {
        Db(
            driver = AndroidSQLiteDriver(),
            path = databaseFile().absolutePath,
        )
    }

    override fun onCreate() {
        super.onCreate()
        Di.init(this)
    }

    fun databaseFile(): File {
        return getDatabasePath("vesti-2026-03-17.org.vestifeed.db")
    }
}

fun Fragment.api() = requireContext().api()

fun Context.api(): Api = (applicationContext as App).api

fun Fragment.db() = requireContext().db()

fun Context.db(): Db = (applicationContext as App).db