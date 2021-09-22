package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class LogQueriesTest {

    lateinit var db: LogQueries

    @Before
    fun setup() {
        db = database().logQueries
    }

    @Test
    fun insert() {
        val row = db.insert()
        Assert.assertEquals(row, db.selectAll().executeAsList().single())
    }

    @Test
    fun `select all`() {
        val rows = listOf(db.insert(), db.insert(), db.insert())
        Assert.assertEquals(rows, db.selectAll().executeAsList().reversed())
    }

    @Test
    fun `select by id`() {
        val row = db.insert()
        Assert.assertEquals(row, db.selectById(row.id).executeAsOne())
    }

    @Test
    fun `select last row id`() {
        val rows = listOf(db.insert(), db.insert())
        Assert.assertEquals(rows.last().id, db.selectLastInsertRowId().executeAsOne())
    }

    @Test
    fun `delete all`() {
        repeat(3) { db.insert() }
        db.deleteAll()
        Assert.assertTrue(db.selectAll().executeAsList().isEmpty())
    }
}

fun LogQueries.insert(): Log {
    insert(
        date = "",
        level = 0,
        tag = "",
        message = "",
        stackTrace = "",
    )

    return selectById(selectLastInsertRowId().executeAsOne()).executeAsOne()
}