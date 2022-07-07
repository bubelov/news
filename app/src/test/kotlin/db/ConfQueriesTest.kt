package db

import conf.ConfRepo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfQueriesTest {

    @Test
    fun insert() = runBlocking {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        assertEquals(ConfRepo.DEFAULT_CONF, db.confQueries.selectAll().executeAsOne())
    }

    @Test
    fun selectAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        assertEquals(ConfRepo.DEFAULT_CONF, db.confQueries.selectAll().executeAsOne())
    }

    @Test
    fun deleteAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepo.DEFAULT_CONF)
        db.confQueries.deleteAll()
        assertNull(db.confQueries.selectAll().executeAsOneOrNull())
    }
}