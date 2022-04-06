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
        db.insertDefault()
        val conf = db.select().executeAsOne()
        val changedConf = conf.copy(authType = "test")
        runBlocking { ConfRepository(db).save(changedConf) }
        assertEquals(changedConf, db.select().executeAsOne())
    }

    @Test
    fun `insert default`() {
        db.insertDefault()
        val conf = db.select().executeAsOne()
        assertEquals(defaultConf(), conf)
    }

    @Test
    fun select() {
        db.insertDefault()
        val conf = db.select().executeAsOne()
        assertEquals(conf, db.select().executeAsOne())
    }

    @Test
    fun `delete all`() {
        db.insertDefault()
        db.deleteAll()
        assertNull(db.select().executeAsOneOrNull())
    }
}

fun defaultConf() = Conf(
    id = 1,
    authType = "",
    nextcloudServerUrl = "",
    nextcloudServerTrustSelfSignedCerts = false,
    nextcloudServerUsername = "",
    nextcloudServerPassword = "",
    minifluxServerUrl = "",
    minifluxServerTrustSelfSignedCerts = false,
    minifluxServerUsername = "",
    minifluxServerPassword = "",
    initialSyncCompleted = false,
    lastEntriesSyncDateTime = "",
    showReadEntries = false,
    sortOrder = "descending",
    showPreviewImages = true,
    cropPreviewImages = true,
    markScrolledEntriesAsRead = false,
    syncOnStartup = true,
    syncInBackground = true,
    backgroundSyncIntervalMillis = 10800000,
    useBuiltInBrowser = true,
)