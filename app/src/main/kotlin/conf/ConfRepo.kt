package conf

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import db.Conf
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
import org.koin.core.annotation.Single

@Single
class ConfRepo(
    private val db: SQLiteDatabase,
) {

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
        val values = ContentValues().apply {
            put("backend", conf.backend)
            put("miniflux_server_url", conf.minifluxServerUrl)
            put("miniflux_server_trust_self_signed_certs", conf.minifluxServerTrustSelfSignedCerts)
            put("miniflux_server_username", conf.minifluxServerUsername)
            put("miniflux_server_password", conf.minifluxServerPassword)
            put("nextcloud_server_url", conf.nextcloudServerUrl)
            put(
                "nextcloud_server_trust_self_signed_certs", conf.nextcloudServerTrustSelfSignedCerts
            )
            put("nextcloud_server_username", conf.nextcloudServerUsername)
            put("nextcloud_server_password", conf.nextcloudServerPassword)
            put("initial_sync_completed", conf.initialSyncCompleted)
            put("last_entries_sync_datetime", conf.lastEntriesSyncDatetime)
            put("show_read_entries", conf.showReadEntries)
            put("sort_order", conf.sortOrder)
            put("show_preview_images", conf.showPreviewImages)
            put("crop_preview_images", conf.cropPreviewImages)
            put("mark_scrolled_entries_as_read", conf.markScrolledEntriesAsRead)
            put("sync_on_startup", conf.syncOnStartup)
            put("sync_in_background", conf.syncInBackground)
            put("background_sync_interval_millis", conf.backgroundSyncIntervalMillis)
            put("use_built_in_browser", conf.useBuiltInBrowser)
            put("show_preview_text", conf.showPreviewText)
            put("synced_on_startup", conf.syncedOnStartup)
        }
        db.insert("conf", null, values)
    }

    fun select(): Conf? {
        val cursor = db.query(
            "conf", arrayOf(
                "backend",
                "miniflux_server_url",
                "miniflux_server_trust_self_signed_certs",
                "miniflux_server_username",
                "miniflux_server_password",
                "nextcloud_server_url",
                "nextcloud_server_trust_self_signed_certs",
                "nextcloud_server_username",
                "nextcloud_server_password",
                "initial_sync_completed",
                "last_entries_sync_datetime",
                "show_read_entries",
                "sort_order",
                "show_preview_images",
                "crop_preview_images",
                "mark_scrolled_entries_as_read",
                "sync_on_startup",
                "sync_in_background",
                "background_sync_interval_millis",
                "use_built_in_browser",
                "show_preview_text",
                "synced_on_startup",
            ), "", emptyArray(), "", "", ""
        )
        if (!cursor.moveToNext()) {
            return null
        } else {
            return Conf(
                backend = cursor.getString(0),
                minifluxServerUrl = cursor.getString(1),
                minifluxServerTrustSelfSignedCerts = cursor.getBoolean(2),
                minifluxServerUsername = cursor.getString(3),
                minifluxServerPassword = cursor.getString(4),
                nextcloudServerUrl = cursor.getString(5),
                nextcloudServerTrustSelfSignedCerts = cursor.getBoolean(6),
                nextcloudServerUsername = cursor.getString(7),
                nextcloudServerPassword = cursor.getString(8),
                initialSyncCompleted = cursor.getBoolean(9),
                lastEntriesSyncDatetime = cursor.getString(10),
                showReadEntries = cursor.getBoolean(11),
                sortOrder = cursor.getString(12),
                showPreviewImages = cursor.getBoolean(13),
                cropPreviewImages = cursor.getBoolean(14),
                markScrolledEntriesAsRead = cursor.getBoolean(15),
                syncOnStartup = cursor.getBoolean(16),
                syncInBackground = cursor.getBoolean(17),
                backgroundSyncIntervalMillis = cursor.getLong(18),
                useBuiltInBrowser = cursor.getBoolean(19),
                showPreviewText = cursor.getBoolean(20),
                syncedOnStartup = cursor.getBoolean(21),
            )
        }
    }

    fun Cursor.getBoolean(columnIndex: Int): Boolean {
        return getInt(columnIndex) == 1
    }

    fun delete() {
        db.delete("conf", "", emptyArray())
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
            minifluxServerUsername = "",
            minifluxServerPassword = "",
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