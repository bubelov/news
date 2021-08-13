package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LogEntryQueriesTests {

    lateinit var db: LogQueries

    @Before
    fun setup() {
        db = database().logQueries
    }

    @Test
    fun insert() {
        val item = log()
        db.insert(item)
        Assert.assertEquals(item, db.selectAll().executeAsList().single())
    }

    @Test
    fun selectAll() {
        val items = listOf(log(), log(), log())
        items.forEach { db.insert(it) }
        Assert.assertEquals(items, db.selectAll().executeAsList())
    }

    @Test
    fun deleteAll() {
        val items = listOf(log(), log(), log())
        items.forEach { db.insert(it) }
        db.deleteAll()
        Assert.assertTrue(db.selectAll().executeAsList().isEmpty())
    }
}

fun log() = Log(
    id = UUID.randomUUID().toString(),
    date = "",
    tag = "",
    message = "",
    stackTrace = "",
)