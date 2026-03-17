package db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun testDb(): Db {
    return Db(BundledSQLiteDriver().open(":memory:"))
}