package common

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Preference
import db.PreferenceQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PreferencesRepository(
    private val db: PreferenceQueries,
) {

    suspend fun getAsFlow(): Flow<Preferences> = withContext(Dispatchers.IO) {
        return@withContext db.selectAll().asFlow().mapToList(Dispatchers.IO).map {
            Preferences().apply {
                it.find { it.key == AUTH_TYPE }?.value?.apply {
                    authType = this
                }

                it.find { it.key == NEXTCLOUD_SERVER_URL }?.value?.apply {
                    nextcloudServerUrl = this
                }

                it.find { it.key == NEXTCLOUD_SERVER_TRUST_SELF_SIGNED_CERTS }?.value?.apply {
                    nextcloudServerTrustSelfSignedCerts = this.toBoolean()
                }

                it.find { it.key == NEXTCLOUD_SERVER_USERNAME }?.value?.apply {
                    nextcloudServerUsername = this
                }

                it.find { it.key == NEXTCLOUD_SERVER_PASSWORD }?.value?.apply {
                    nextcloudServerPassword = this
                }

                it.find { it.key == MINIFLUX_SERVER_URL }?.value?.apply {
                    minifluxServerUrl = this
                }

                it.find { it.key == MINIFLUX_SERVER_TRUST_SELF_SIGNED_CERTS }?.value?.apply {
                    minifluxServerTrustSelfSignedCerts = this.toBoolean()
                }

                it.find { it.key == MINIFLUX_SERVER_USERNAME }?.value?.apply {
                    minifluxServerUsername = this
                }

                it.find { it.key == MINIFLUX_SERVER_PASSWORD }?.value?.apply {
                    minifluxServerPassword = this
                }

                it.find { it.key == INITIAL_SYNC_COMPLETED }?.value?.apply {
                    initialSyncCompleted = this.toBoolean()
                }

                it.find { it.key == LAST_ENTRIES_SYNC_DATE_TIME }?.value?.apply {
                    lastEntriesSyncDateTime = this
                }

                it.find { it.key == SHOW_OPENED_ENTRIES }?.value?.apply {
                    showOpenedEntries = this.toBoolean()
                }

                it.find { it.key == SORT_ORDER }?.value?.apply {
                    sortOrder = this
                }

                it.find { it.key == SHOW_PREVIEW_IMAGES }?.value?.apply {
                    showPreviewImages = this.toBoolean()
                }

                it.find { it.key == CROP_PREVIEW_IMAGES }?.value?.apply {
                    cropPreviewImages = this.toBoolean()
                }

                it.find { it.key == MARK_SCROLLED_ENTRIES_AS_READ }?.value?.apply {
                    markScrolledEntriesAsRead = this.toBoolean()
                }

                it.find { it.key == SYNC_ON_STARTUP }?.value?.apply {
                    syncOnStartup = this.toBoolean()
                }

                it.find { it.key == SYNC_IN_BACKGROUND }?.value?.apply {
                    syncInBackground = this.toBoolean()
                }

                it.find { it.key == BACKGROUND_SYNC_INTERVAL_MILLIS }?.value?.apply {
                    backgroundSyncIntervalMillis = this.toLong()
                }
            }
        }
    }

    suspend fun get() = getAsFlow().first()

    suspend fun save(action: Preferences.() -> Unit) {
        val preferences = get().apply(action)

        preferences.apply {
            db.transaction {
                putString(AUTH_TYPE, authType)
                putString(NEXTCLOUD_SERVER_URL, nextcloudServerUrl)
                putString(NEXTCLOUD_SERVER_USERNAME, nextcloudServerUsername)
                putBoolean(
                    NEXTCLOUD_SERVER_TRUST_SELF_SIGNED_CERTS,
                    nextcloudServerTrustSelfSignedCerts
                )
                putString(NEXTCLOUD_SERVER_PASSWORD, nextcloudServerPassword)
                putString(MINIFLUX_SERVER_URL, minifluxServerUrl)
                putBoolean(
                    MINIFLUX_SERVER_TRUST_SELF_SIGNED_CERTS,
                    minifluxServerTrustSelfSignedCerts
                )
                putString(MINIFLUX_SERVER_USERNAME, minifluxServerUsername)
                putString(MINIFLUX_SERVER_PASSWORD, minifluxServerPassword)

                putBoolean(INITIAL_SYNC_COMPLETED, initialSyncCompleted)
                putString(LAST_ENTRIES_SYNC_DATE_TIME, lastEntriesSyncDateTime)
                putBoolean(SHOW_OPENED_ENTRIES, showOpenedEntries)
                putString(SORT_ORDER, sortOrder)
                putBoolean(SHOW_PREVIEW_IMAGES, showPreviewImages)
                putBoolean(CROP_PREVIEW_IMAGES, cropPreviewImages)
                putBoolean(MARK_SCROLLED_ENTRIES_AS_READ, markScrolledEntriesAsRead)
                putBoolean(SYNC_ON_STARTUP, syncOnStartup)
                putBoolean(SYNC_IN_BACKGROUND, syncInBackground)
                putString(BACKGROUND_SYNC_INTERVAL_MILLIS, backgroundSyncIntervalMillis.toString())
            }
        }
    }

    private fun putString(key: String, value: String) {
        db.insertOrReplace(Preference(key, value))
    }

    private fun putBoolean(key: String, value: Boolean) {
        putString(key, if (value) "true" else "false")
    }

    private fun String.toBoolean(): Boolean {
        return when (this.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw Exception()
        }
    }

    companion object {
        const val AUTH_TYPE = "auth_type"
        const val AUTH_TYPE_NEXTCLOUD_APP = "nextcloud_app"
        const val AUTH_TYPE_NEXTCLOUD_DIRECT = "nextcloud_direct"
        const val NEXTCLOUD_SERVER_URL = "nextcloud_server_url"
        const val NEXTCLOUD_SERVER_TRUST_SELF_SIGNED_CERTS =
            "nextcloud_server_trust_self_signed_certs"
        const val NEXTCLOUD_SERVER_USERNAME = "nextcloud_server_username"
        const val NEXTCLOUD_SERVER_PASSWORD = "nextcloud_server_password"
        const val AUTH_TYPE_MINIFLUX = "miniflux"
        const val MINIFLUX_SERVER_URL = "miniflux_server_url"
        const val MINIFLUX_SERVER_TRUST_SELF_SIGNED_CERTS = "miniflux_server_trust_self_signed_certs"
        const val MINIFLUX_SERVER_USERNAME = "miniflux_server_username"
        const val MINIFLUX_SERVER_PASSWORD = "miniflux_server_password"
        const val AUTH_TYPE_STANDALONE = "standalone"

        const val INITIAL_SYNC_COMPLETED = "initial_sync_completed"
        const val LAST_ENTRIES_SYNC_DATE_TIME = "last_entries_sync_date_time"

        const val SHOW_OPENED_ENTRIES = "show_opened_entries"

        const val SORT_ORDER = "sort_order"
        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        const val SHOW_PREVIEW_IMAGES = "show_preview_images"
        const val CROP_PREVIEW_IMAGES = "crop_preview_images"

        const val MARK_SCROLLED_ENTRIES_AS_READ = "mark_scrolled_entries_as_read"

        const val SYNC_ON_STARTUP = "sync_on_startup"

        const val SYNC_IN_BACKGROUND = "sync_in_background"
        const val BACKGROUND_SYNC_INTERVAL_MILLIS = "background_sync_interval_millis"
    }
}