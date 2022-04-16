package db

import common.ConfRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfQueriesTest {
    private lateinit var db: ConfQueries

    @Before
    fun setup() {
        db = database().confQueries
    }

    @Test
    fun `insert or replace`() {
        runBlocking { ConfRepository(db).insert(ConfRepository.DEFAULT_CONF) }
        val oldConf = db.select().executeAsOne()
        val newConf = oldConf.copy(authType = "test")
        runBlocking { ConfRepository(db).insert(newConf) }
        assertEquals(newConf, db.select().executeAsOne())
    }

    @Test
    fun `insert default`() {
        runBlocking { ConfRepository(db).insert(ConfRepository.DEFAULT_CONF) }
        val conf = db.select().executeAsOne()
        assertEquals(ConfRepository.DEFAULT_CONF, conf)
    }

    @Test
    fun select() {
        runBlocking { ConfRepository(db).insert(ConfRepository.DEFAULT_CONF) }
        val conf = db.select().executeAsOne()
        assertEquals(conf, db.select().executeAsOne())
    }

    @Test
    fun `delete all`() {
        runBlocking { ConfRepository(db).insert(ConfRepository.DEFAULT_CONF) }
        db.delete()
        assertNull(db.select().executeAsOneOrNull())
    }
}