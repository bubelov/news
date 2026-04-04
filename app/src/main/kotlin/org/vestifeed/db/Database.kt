package org.vestifeed.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import org.vestifeed.db.table.ConfQueries
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.EntrySchema
import org.vestifeed.db.table.FEED_SCHEMA
import org.vestifeed.db.table.FeedQueries

class Database(driver: SQLiteDriver, val path: String) {

    private val conn = driver.open(path)

    val feed = FeedQueries(conn)
    val entry = EntryQueries(conn)
    val conf = ConfQueries(conn)

    init {
        migrate()
    }

    private fun migrate() {
        val stmt = conn.prepare("SELECT user_version FROM pragma_user_version;")
        val version = if (stmt.step()) stmt.getInt(0) else 0

        if (version == 0) {
            conn.execSQL(FEED_SCHEMA)
            conn.execSQL(EntrySchema.toString())
            conn.execSQL(ConfSchema.toString())
            conn.execSQL("PRAGMA user_version=1;")
        }
    }

    fun transaction(block: () -> Unit) {
        conn.execSQL("BEGIN TRANSACTION;")
        try {
            block()
            conn.execSQL("COMMIT;")
        } catch (e: Exception) {
            conn.execSQL("ROLLBACK;")
            throw e
        }
    }
}