package db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun testDb(): Db {
    val db = Db(BundledSQLiteDriver(), ":memory:")
    executeSchema(db.conn)
    return db
}