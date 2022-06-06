package db

import common.ConfRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfQueriesTest {

    @Test
    fun `insert or replace`() {
        val db = testDb()
        runBlocking { ConfRepository(db).upsert(ConfRepository.DEFAULT_CONF) }
        val oldConf = db.confQueries.select().executeAsOne()
        val newConf = oldConf.copy(backend = ConfRepository.BACKEND_STANDALONE)
        runBlocking { ConfRepository(db).upsert(newConf) }
        assertEquals(newConf, db.confQueries.select().executeAsOne())
    }

    @Test
    fun `insert default`() {
        val db = testDb()
        runBlocking { ConfRepository(db).upsert(ConfRepository.DEFAULT_CONF) }
        val conf = db.confQueries.select().executeAsOne()
        assertEquals(ConfRepository.DEFAULT_CONF, conf)
    }

    @Test
    fun select() {
        val db = testDb()
        runBlocking { ConfRepository(db).upsert(ConfRepository.DEFAULT_CONF) }
        val conf = db.confQueries.select().executeAsOne()
        assertEquals(conf, db.confQueries.select().executeAsOne())
    }

    @Test
    fun `delete all`() {
        val db = testDb()
        runBlocking { ConfRepository(db).upsert(ConfRepository.DEFAULT_CONF) }
        db.confQueries.delete()
        assertNull(db.confQueries.select().executeAsOneOrNull())
    }
}