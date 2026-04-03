package org.vestifeed.db.table

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database

class ConfTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun confSchema_tableName() {
        assertEquals("conf", ConfSchema.TABLE_NAME)
    }

    @Test
    fun confSchema_columns() {
        val columns = ConfSchema.Columns.entries
        assertEquals(17, columns.size)
        assertEquals("backend", columns[0].sqlName)
        assertEquals("miniflux_server_url", columns[1].sqlName)
        assertEquals("miniflux_server_trust_self_signed_certs", columns[2].sqlName)
        assertEquals("miniflux_server_token", columns[3].sqlName)
        assertEquals("initial_sync_completed", columns[4].sqlName)
        assertEquals("last_entries_sync_datetime", columns[5].sqlName)
        assertEquals("show_read_entries", columns[6].sqlName)
        assertEquals("sort_order", columns[7].sqlName)
        assertEquals("show_preview_images", columns[8].sqlName)
        assertEquals("crop_preview_images", columns[9].sqlName)
        assertEquals("mark_scrolled_entries_as_read", columns[10].sqlName)
        assertEquals("sync_on_startup", columns[11].sqlName)
        assertEquals("sync_in_background", columns[12].sqlName)
        assertEquals("background_sync_interval_millis", columns[13].sqlName)
        assertEquals("use_built_in_browser", columns[14].sqlName)
        assertEquals("show_preview_text", columns[15].sqlName)
        assertEquals("synced_on_startup", columns[16].sqlName)
    }

    @Test
    fun confSchema_constants() {
        assertEquals("standalone", ConfSchema.BACKEND_STANDALONE)
        assertEquals("miniflux", ConfSchema.BACKEND_MINIFLUX)
        assertEquals("ascending", ConfSchema.SORT_ORDER_ASCENDING)
        assertEquals("descending", ConfSchema.SORT_ORDER_DESCENDING)
    }

    @Test
    fun confSchema_createTableStatement() {
        val statement = ConfSchema.toString()
        assertTrue(statement.contains("CREATE TABLE conf"))
        assertTrue(statement.contains("backend TEXT NOT NULL"))
        assertTrue(statement.contains("miniflux_server_url TEXT NOT NULL"))
        assertTrue(statement.contains("miniflux_server_trust_self_signed_certs INTEGER NOT NULL"))
        assertTrue(statement.contains("background_sync_interval_millis INTEGER NOT NULL"))
    }

    @Test
    fun confSchema_columnsString() {
        val columns = ConfSchema.columns
        assertEquals(
            "backend,miniflux_server_url,miniflux_server_trust_self_signed_certs,miniflux_server_token,initial_sync_completed,last_entries_sync_datetime,show_read_entries,sort_order,show_preview_images,crop_preview_images,mark_scrolled_entries_as_read,sync_on_startup,sync_in_background,background_sync_interval_millis,use_built_in_browser,show_preview_text,synced_on_startup",
            columns
        )
    }

    @Test
    fun confDefaults_values() {
        val defaultConf = confDefault()
        assertEquals("", defaultConf.backend)
        assertEquals("", defaultConf.minifluxServerUrl)
        assertFalse(defaultConf.minifluxServerTrustSelfSignedCerts)
        assertEquals("", defaultConf.minifluxServerToken)
        assertFalse(defaultConf.initialSyncCompleted)
        assertEquals("", defaultConf.lastEntriesSyncDatetime)
        assertFalse(defaultConf.showReadEntries)
        assertEquals(ConfSchema.SORT_ORDER_DESCENDING, defaultConf.sortOrder)
        assertTrue(defaultConf.showPreviewImages)
        assertTrue(defaultConf.cropPreviewImages)
        assertFalse(defaultConf.markScrolledEntriesAsRead)
        assertTrue(defaultConf.syncOnStartup)
        assertTrue(defaultConf.syncInBackground)
        assertEquals(10800000L, defaultConf.backgroundSyncIntervalMillis)
        assertTrue(defaultConf.useBuiltInBrowser)
        assertTrue(defaultConf.showPreviewText)
        assertFalse(defaultConf.syncedOnStartup)
    }

    @Test
    fun confQueries_select_emptyReturnsDefault() {
        val result = db.conf.select()
        val defaultConf = confDefault()
        assertEquals(defaultConf.backend, result.backend)
        assertEquals(defaultConf.sortOrder, result.sortOrder)
        assertEquals(defaultConf.backgroundSyncIntervalMillis, result.backgroundSyncIntervalMillis)
    }

    @Test
    fun confQueries_insertAndSelect() {
        val conf = createConf()
        db.conf.insert(conf)

        val result = db.conf.select()
        assertEquals(conf.backend, result.backend)
        assertEquals(conf.minifluxServerUrl, result.minifluxServerUrl)
        assertEquals(conf.minifluxServerTrustSelfSignedCerts, result.minifluxServerTrustSelfSignedCerts)
        assertEquals(conf.minifluxServerToken, result.minifluxServerToken)
        assertEquals(conf.initialSyncCompleted, result.initialSyncCompleted)
        assertEquals(conf.lastEntriesSyncDatetime, result.lastEntriesSyncDatetime)
        assertEquals(conf.showReadEntries, result.showReadEntries)
        assertEquals(conf.sortOrder, result.sortOrder)
        assertEquals(conf.showPreviewImages, result.showPreviewImages)
        assertEquals(conf.cropPreviewImages, result.cropPreviewImages)
        assertEquals(conf.markScrolledEntriesAsRead, result.markScrolledEntriesAsRead)
        assertEquals(conf.syncOnStartup, result.syncOnStartup)
        assertEquals(conf.syncInBackground, result.syncInBackground)
        assertEquals(conf.backgroundSyncIntervalMillis, result.backgroundSyncIntervalMillis)
        assertEquals(conf.useBuiltInBrowser, result.useBuiltInBrowser)
        assertEquals(conf.showPreviewText, result.showPreviewText)
        assertEquals(conf.syncedOnStartup, result.syncedOnStartup)
    }

    @Test
    fun confQueries_insert_replacesExisting() {
        val conf1 = createConf(backend = ConfSchema.BACKEND_STANDALONE)
        db.conf.insert(conf1)

        db.conf.delete()

        val conf2 = createConf(backend = ConfSchema.BACKEND_MINIFLUX)
        db.conf.insert(conf2)

        val result = db.conf.select()
        assertEquals(ConfSchema.BACKEND_MINIFLUX, result.backend)
    }

    @Test
    fun confQueries_update() {
        val initialConf = createConf(
            backend = ConfSchema.BACKEND_STANDALONE,
            showPreviewImages = false,
        )
        db.conf.insert(initialConf)

        db.conf.update { it.copy(backend = ConfSchema.BACKEND_MINIFLUX, showPreviewImages = true) }

        val result = db.conf.select()
        assertEquals(ConfSchema.BACKEND_MINIFLUX, result.backend)
        assertTrue(result.showPreviewImages)
    }

    @Test
    fun confQueries_delete() {
        val conf = createConf()
        db.conf.insert(conf)
        assertEquals(conf.backend, db.conf.select().backend)

        db.conf.delete()
        val defaultConf = confDefault()
        assertEquals(defaultConf.backend, db.conf.select().backend)
    }

    @Test
    fun confQueries_updatePartialFields() {
        db.conf.insert(createConf(sortOrder = ConfSchema.SORT_ORDER_ASCENDING))

        db.conf.update { it.copy(syncOnStartup = false) }

        val result = db.conf.select()
        assertEquals(ConfSchema.SORT_ORDER_ASCENDING, result.sortOrder)
        assertFalse(result.syncOnStartup)
    }

    private fun createConf(
        backend: String = ConfSchema.BACKEND_STANDALONE,
        minifluxServerUrl: String = "https://miniflux.example.com",
        minifluxServerTrustSelfSignedCerts: Boolean = false,
        minifluxServerToken: String = "miniflux-token",
        initialSyncCompleted: Boolean = true,
        lastEntriesSyncDatetime: String = "2024-01-01T00:00:00Z",
        showReadEntries: Boolean = true,
        sortOrder: String = ConfSchema.SORT_ORDER_DESCENDING,
        showPreviewImages: Boolean = true,
        cropPreviewImages: Boolean = false,
        markScrolledEntriesAsRead: Boolean = true,
        syncOnStartup: Boolean = false,
        syncInBackground: Boolean = false,
        backgroundSyncIntervalMillis: Long = 3600000L,
        useBuiltInBrowser: Boolean = false,
        showPreviewText: Boolean = false,
        syncedOnStartup: Boolean = true,
    ) = Conf(
        backend = backend,
        minifluxServerUrl = minifluxServerUrl,
        minifluxServerTrustSelfSignedCerts = minifluxServerTrustSelfSignedCerts,
        minifluxServerToken = minifluxServerToken,
        initialSyncCompleted = initialSyncCompleted,
        lastEntriesSyncDatetime = lastEntriesSyncDatetime,
        showReadEntries = showReadEntries,
        sortOrder = sortOrder,
        showPreviewImages = showPreviewImages,
        cropPreviewImages = cropPreviewImages,
        markScrolledEntriesAsRead = markScrolledEntriesAsRead,
        syncOnStartup = syncOnStartup,
        syncInBackground = syncInBackground,
        backgroundSyncIntervalMillis = backgroundSyncIntervalMillis,
        useBuiltInBrowser = useBuiltInBrowser,
        showPreviewText = showPreviewText,
        syncedOnStartup = syncedOnStartup,
    )
}