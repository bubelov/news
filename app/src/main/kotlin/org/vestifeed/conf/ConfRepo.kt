package org.vestifeed.conf

import androidx.sqlite.execSQL
import org.vestifeed.db.Conf
import org.vestifeed.db.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ConfRepo(
    private val db: Db,
) {

    private val conn = db.conn

    private val _conf: MutableStateFlow<Conf> = MutableStateFlow(
        runBlocking { select() ?: DEFAULT_CONF })

    val conf: StateFlow<Conf> = _conf.asStateFlow()

    init {
        conf.onEach {
            withContext(Dispatchers.IO) {
                db.transaction {
                    delete()
                    insert(it)
                }
            }
        }.launchIn(GlobalScope)
    }

    fun update(newConf: (Conf) -> Conf) {
        _conf.update { newConf(conf.value) }
    }

    fun insert(conf: Conf) {
        val stmt = conn.prepare("""
            INSERT OR REPLACE INTO conf (
                backend, miniflux_server_url, miniflux_server_trust_self_signed_certs,
                miniflux_server_token, nextcloud_server_url,
                nextcloud_server_trust_self_signed_certs, nextcloud_server_username,
                nextcloud_server_password, initial_sync_completed, last_entries_sync_datetime,
                show_read_entries, sort_order, show_preview_images, crop_preview_images,
                mark_scrolled_entries_as_read, sync_on_startup, sync_in_background,
                background_sync_interval_millis, use_built_in_browser, show_preview_text,
                synced_on_startup
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """)
        stmt.bindText(1, conf.backend)
        stmt.bindText(2, conf.minifluxServerUrl)
        stmt.bindInt(3, if (conf.minifluxServerTrustSelfSignedCerts) 1 else 0)
        stmt.bindText(4, conf.minifluxServerToken)
        stmt.bindText(5, conf.nextcloudServerUrl)
        stmt.bindInt(6, if (conf.nextcloudServerTrustSelfSignedCerts) 1 else 0)
        stmt.bindText(7, conf.nextcloudServerUsername)
        stmt.bindText(8, conf.nextcloudServerPassword)
        stmt.bindInt(9, if (conf.initialSyncCompleted) 1 else 0)
        stmt.bindText(10, conf.lastEntriesSyncDatetime)
        stmt.bindInt(11, if (conf.showReadEntries) 1 else 0)
        stmt.bindText(12, conf.sortOrder)
        stmt.bindInt(13, if (conf.showPreviewImages) 1 else 0)
        stmt.bindInt(14, if (conf.cropPreviewImages) 1 else 0)
        stmt.bindInt(15, if (conf.markScrolledEntriesAsRead) 1 else 0)
        stmt.bindInt(16, if (conf.syncOnStartup) 1 else 0)
        stmt.bindInt(17, if (conf.syncInBackground) 1 else 0)
        stmt.bindLong(18, conf.backgroundSyncIntervalMillis)
        stmt.bindInt(19, if (conf.useBuiltInBrowser) 1 else 0)
        stmt.bindInt(20, if (conf.showPreviewText) 1 else 0)
        stmt.bindInt(21, if (conf.syncedOnStartup) 1 else 0)
        stmt.step()
        stmt.close()
    }

    fun select(): Conf? {
        val stmt = conn.prepare("""
            SELECT
                backend,
                miniflux_server_url,
                miniflux_server_trust_self_signed_certs,
                miniflux_server_token,
                nextcloud_server_url,
                nextcloud_server_trust_self_signed_certs,
                nextcloud_server_username,
                nextcloud_server_password,
                initial_sync_completed,
                last_entries_sync_datetime,
                show_read_entries,
                sort_order,
                show_preview_images,
                crop_preview_images,
                mark_scrolled_entries_as_read,
                sync_on_startup,
                sync_in_background,
                background_sync_interval_millis,
                use_built_in_browser,
                show_preview_text,
                synced_on_startup
            FROM conf
        """)
        if (!stmt.step()) {
            stmt.close()
            return null
        }
        return Conf(
            backend = stmt.getText(0),
            minifluxServerUrl = stmt.getText(1),
            minifluxServerTrustSelfSignedCerts = stmt.getInt(2) == 1,
            minifluxServerToken = stmt.getText(3),
            nextcloudServerUrl = stmt.getText(4),
            nextcloudServerTrustSelfSignedCerts = stmt.getInt(5) == 1,
            nextcloudServerUsername = stmt.getText(6),
            nextcloudServerPassword = stmt.getText(7),
            initialSyncCompleted = stmt.getInt(8) == 1,
            lastEntriesSyncDatetime = stmt.getText(9),
            showReadEntries = stmt.getInt(10) == 1,
            sortOrder = stmt.getText(11),
            showPreviewImages = stmt.getInt(12) == 1,
            cropPreviewImages = stmt.getInt(13) == 1,
            markScrolledEntriesAsRead = stmt.getInt(14) == 1,
            syncOnStartup = stmt.getInt(15) == 1,
            syncInBackground = stmt.getInt(16) == 1,
            backgroundSyncIntervalMillis = stmt.getLong(17),
            useBuiltInBrowser = stmt.getInt(18) == 1,
            showPreviewText = stmt.getInt(19) == 1,
            syncedOnStartup = stmt.getInt(20) == 1,
        )
    }

    fun delete() {
        conn.execSQL("DELETE FROM conf")
    }

    companion object {
        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"
        const val BACKEND_NEXTCLOUD = "nextcloud"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        val DEFAULT_CONF = Conf(
            backend = "",
            minifluxServerUrl = "",
            minifluxServerTrustSelfSignedCerts = false,
            minifluxServerToken = "",
            nextcloudServerUrl = "",
            nextcloudServerTrustSelfSignedCerts = false,
            nextcloudServerUsername = "",
            nextcloudServerPassword = "",
            initialSyncCompleted = false,
            lastEntriesSyncDatetime = "",
            showReadEntries = false,
            sortOrder = SORT_ORDER_DESCENDING,
            showPreviewImages = true,
            cropPreviewImages = true,
            markScrolledEntriesAsRead = false,
            syncOnStartup = true,
            syncInBackground = true,
            backgroundSyncIntervalMillis = 10800000,
            useBuiltInBrowser = true,
            showPreviewText = true,
            syncedOnStartup = false,
        )
    }
}
