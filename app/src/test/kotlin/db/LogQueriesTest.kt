package db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

class LogQueriesTest {

    lateinit var db: LogQueries

    @Before
    fun setup() {
        db = database().logQueries
    }

    @Test
    fun insert() {
        val row = db.insert()
        assertEquals(row, db.selectAll().executeAsList().single())
    }

    @Test
    fun `select all`() {
        val rows = listOf(db.insert(), db.insert(), db.insert())
        assertEquals(rows, db.selectAll().executeAsList().reversed())
    }

    @Test
    fun `select by id`() {
        val row = db.insert()
        assertEquals(row, db.selectById(row.id).executeAsOne())
    }

    @Test
    fun `select last insert row id`() {
        val rows = listOf(db.insert(), db.insert())
        assertEquals(rows.last().id, db.selectLastInsertRowId().executeAsOne())
    }

    @Test
    fun `select count`() {
        val count = 5
        repeat(count) { db.insert() }
        assertEquals(5, db.selectCount().executeAsOne())
    }

    @Test
    fun `delete all`() {
        repeat(3) { db.insert() }
        db.deleteAll()
        assertTrue(db.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun `delete where date less than`() {
        val now = OffsetDateTime.now()
        val dayAgo = now.minusDays(1)
        val weekAgo = now.minusWeeks(1)
        val monthAgo = now.minusMonths(1)
        val yearAgo = now.minusYears(1)

        val rows = listOf(
            db.insert(now.toString()),
            db.insert(dayAgo.toString()),
            db.insert(weekAgo.toString()),
            db.insert(monthAgo.toString()),
            db.insert(yearAgo.toString()),
        )

        db.deleteWhereDateLessThan(OffsetDateTime.now().minusDays(2).toString())
        assertEquals(rows.subList(0, 2), db.selectAll().executeAsList())
    }
}

fun LogQueries.insert(date: String = ""): Log {
    insert(
        date = date,
        level = 0,
        tag = "",
        message = "",
        stackTrace = "",
    )

    return selectById(selectLastInsertRowId().executeAsOne()).executeAsOne()
}