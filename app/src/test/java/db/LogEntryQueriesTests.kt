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
        val item = logEntry()
        db.insert(item)
        Assert.assertEquals(item, db.selectAll().executeAsList().single())
    }

    @Test
    fun selectAll() {
        val items = listOf(logEntry(), logEntry(), logEntry())
        items.forEach { db.insert(it) }
        Assert.assertEquals(items, db.selectAll().executeAsList())
    }

    @Test
    fun deleteAll() {
        val items = listOf(logEntry(), logEntry(), logEntry())
        items.forEach { db.insert(it) }
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