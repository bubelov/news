package db

import conf.ConfRepo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class ConfQueriesTest {

    @Test
    fun insert() = runBlocking {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        assertEquals(ConfRepo.DEFAULT_CONF, db.confQueries.select().executeAsOne())
    }

    @Test
    fun selectAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        assertEquals(ConfRepo.DEFAULT_CONF, db.confQueries.select().executeAsOne())
    }

    @Test
    fun deleteAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        db.confQueries.delete()
        assertNull(db.confQueries.select().executeAsOneOrNull())
    }
}