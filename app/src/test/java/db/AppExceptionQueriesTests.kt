package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class AppExceptionQueriesTests {

    lateinit var db: LoggedExceptionQueries

    @Before
    fun setup() {
        db = database().loggedExceptionQueries
    }

    @Test
    fun insert() {
        val item = appException()
        db.insert(item)
        Assert.assertEquals(item, db.selectAll().executeAsList().single())
    }

    @Test
    fun selectAll() {
        val items = listOf(appException(), appException(), appException())
        items.forEach { db.insert(it) }
        Assert.assertEquals(items, db.selectAll().executeAsList())
    }

    @Test
    fun selectById() {
        val item = appException()
        db.insert(item)
        Assert.assertEquals(item, db.selectById(item.id).executeAsOne())
    }

    @Test
    fun deleteAll() {
        val items = listOf(appException(), appException(), appException())
        items.forEach { db.insert(it) }
        db.deleteAll()
        Assert.assertTrue(db.selectAll().executeAsList().isEmpty())
    }
}

fun appException() = LoggedException(
    id = UUID.randomUUID().toString(),
    date = "",
    exceptionClass = "",
    message = "",
    stackTrace = "",
)
