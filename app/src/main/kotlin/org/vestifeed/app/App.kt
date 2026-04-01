package org.vestifeed.app

import android.app.Application
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.vestifeed.api.Api
import org.vestifeed.api.HotSwapApi
import org.vestifeed.db.Database
import org.vestifeed.sync.Sync
import java.io.File

class App : Application() {
    val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val sync by lazy { Sync(scope, api, db) }

    val api by lazy { HotSwapApi(db) }

    val db by lazy {
        Database(
            driver = AndroidSQLiteDriver(),
            path = databaseFile().absolutePath,
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        scope.cancel()
    }

    fun databaseFile(): File {
        return getDatabasePath("vesti.db")
    }
}

fun Fragment.sync() = requireContext().sync()

fun Context.sync(): Sync = (applicationContext as App).sync

fun Fragment.api() = requireContext().api()

fun Context.api(): Api = (applicationContext as App).api

fun Fragment.db() = requireContext().db()

fun Context.db(): Database = (applicationContext as App).db