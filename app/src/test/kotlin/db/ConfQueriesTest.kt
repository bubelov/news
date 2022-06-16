package db

import common.ConfRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfQueriesTest {

    @Test
    fun insert() = runBlocking {
        val db = testDb()
        db.confQueries.insert(ConfRepository.DEFAULT_CONF)
        assertEquals(ConfRepository.DEFAULT_CONF, db.confQueries.selectAll().executeAsOne())
    }

    @Test
    fun selectAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepository.DEFAULT_CONF)
        assertEquals(ConfRepository.DEFAULT_CONF, db.confQueries.selectAll().executeAsOne())
    }

    @Test
    fun deleteAll() {
        val db = testDb()
        db.confQueries.insert(ConfRepository.DEFAULT_CONF)
        db.confQueries.deleteAll()
        assertNull(db.confQueries.selectAll().executeAsOneOrNull())
    }
}