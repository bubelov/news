package org.vestifeed.app

import android.app.Application
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.sqlite.driver.AndroidSQLiteDriver
import org.vestifeed.api.Api
import org.vestifeed.api.HotSwapApi
import org.vestifeed.db.Database
import org.vestifeed.entries.EntriesRepo
import org.vestifeed.feeds.FeedsRepo
import org.vestifeed.sync.Sync
import java.io.File

class App : Application() {

    val sync by lazy { Sync(db, FeedsRepo(api, db), EntriesRepo(api, db)) }

    val api by lazy { HotSwapApi(db) }

    val db by lazy {
        Database(
            driver = AndroidSQLiteDriver(),
            path = databaseFile().absolutePath,
        )
    }

    fun databaseFile(): File {
        return getDatabasePath("vesti-2026-03-17.org.vestifeed.db")
    }
}

fun Fragment.sync() = requireContext().sync()

fun Context.sync(): Sync = (applicationContext as App).sync

fun Fragment.api() = requireContext().api()

fun Context.api(): Api = (applicationContext as App).api

fun Fragment.db() = requireContext().db()

fun Context.db(): Database = (applicationContext as App).db