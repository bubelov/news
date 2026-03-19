package db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun db() = Db(BundledSQLiteDriver(), ":memory:")