package app

import android.app.Application
import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import db.Db
import di.Di
import java.io.File

class App : Application() {

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
        return getDatabasePath("vesti-2026-03-17.db")
    }
}

fun Context.db(): Db = (applicationContext as App).db