package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class LogEntryQueriesTests {

    lateinit var db: LogEntryQueries

    @Before
    fun setup() {
        db = database().logEntryQueries
    }

    @Test
    fun insert() {
        val entry = logEntry()
        db.insert(entry)
        Assert.assertEquals(entry, db.selectAll().executeAsList().single())
    }

    @Test
    fun selectAll() {
        val entries = listOf(logEntry(), logEntry(), logEntry())
        entries.forEach { db.insert(it) }
        Assert.assertEquals(entries, db.selectAll().executeAsList())
    }

    @Test
    fun deleteAll() {
        val entries = listOf(logEntry(), logEntry(), logEntry())
        entries.forEach { db.insert(it) }
        db.deleteAll()
        Assert.assertTrue(db.selectAll().executeAsList().isEmpty())
    }
}

fun logEntry() = LogEntry(
    id = UUID.randomUUID().toString(),
    date = "",
    tag = "",
    message = "",
)