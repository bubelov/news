package db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun testDb(): Db {
    val conn = BundledSQLiteDriver().open(":memory:")
    executeSchema(conn)
    return Db(conn, false)
}