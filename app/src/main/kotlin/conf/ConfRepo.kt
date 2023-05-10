package conf

import db.Conf
import db.Db
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
    private val db: Db,
) {

    private val _conf: MutableStateFlow<Conf> = MutableStateFlow(
        runBlocking { db.confQueries.select().executeAsOneOrNull() ?: DEFAULT_CONF }
    )

    val conf: StateFlow<Conf> = _conf.asStateFlow()

    init {
        conf.onEach {
            withContext(Dispatchers.IO) {
                db.transaction {
                    db.confQueries.delete()
                    db.confQueries.insert(it)
                }
            }
        }.launchIn(GlobalScope)
    }

    fun update(newConf: (Conf) -> Conf) {
        _conf.update { newConf(conf.value) }
    }

    companion object {
        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"
        const val BACKEND_NEXTCLOUD = "nextcloud"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        val DEFAULT_CONF = Conf(
            backend = "",
            miniflux_server_url = "",
            miniflux_server_trust_self_signed_certs = false,
            miniflux_server_username = "",
            miniflux_server_password = "",
            nextcloud_server_url = "",
            nextcloud_server_trust_self_signed_certs = false,
            nextcloud_server_username = "",
            nextcloud_server_password = "",
            initial_sync_completed = false,
            last_entries_sync_datetime = "",
            show_read_entries = false,
            sort_order = SORT_ORDER_DESCENDING,
            show_preview_images = true,
            crop_preview_images = true,
            mark_scrolled_entries_as_read = false,
            sync_on_startup = true,
            sync_in_background = true,
            background_sync_interval_millis = 10800000,
            use_built_in_browser = true,
            show_preview_text = true,
            synced_on_startup = false,
        )
    }
}