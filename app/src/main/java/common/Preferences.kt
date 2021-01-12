package common

import db.Preference
import db.PreferenceQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class Preferences(
    private val db: PreferenceQueries
) {

    suspend fun getString(key: String) = withContext(Dispatchers.IO) {
        db.selectByKey(key).asFlow().map {
            it.executeAsOneOrNull()?.value ?: getDefaultStringValueOrEmpty(key)
        }
    }

    fun getStringBlocking(key: String) = runBlocking { getString(key).first() }

    suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        db.insertOrReplace(Preference(key, value))
    }

    fun putStringBlocking(key: String, value: String) {
        runBlocking { putString(key, value) }
    }

    suspend fun getBoolean(key: String) = getString(key).map {
        when (it) {
            "true" -> true
            "false" -> false
            else -> getDefaultBooleanValue(key)
        }
    }

    fun getBooleanBlocking(key: String): Boolean {
        return runBlocking { getBoolean(key).first() }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        putString(key, if (value) "true" else "false")
    }

    fun putBooleanBlocking(key: String, value: Boolean) = runBlocking { putBoolean(key, value) }

    fun getCount() = db.selectCount().asFlow().map { it.executeAsOne() }

    private fun getDefaultStringValueOrEmpty(key: String): String {
        return when (key) {
            SORT_ORDER -> SORT_ORDER_DESCENDING
            else -> ""
        }
    }

    private fun getDefaultBooleanValue(key: String): Boolean {
        return when (key) {
            INITIAL_SYNC_COMPLETED -> false
            SHOW_OPENED_ENTRIES -> false
            SHOW_PREVIEW_IMAGES -> true
            CROP_PREVIEW_IMAGES -> true
            else -> throw Exception("No defaults for key $key")
        }
    }

    companion object {
        const val AUTH_TYPE = "auth_type"
        const val AUTH_TYPE_STANDALONE = "standalone"
        const val AUTH_TYPE_NEXTCLOUD_APP = "nextcloud_app"
        const val AUTH_TYPE_NEXTCLOUD_DIRECT = "nextcloud_direct"
        const val NEXTCLOUD_SERVER_URL = "nextcloud_server_url"
        const val NEXTCLOUD_SERVER_USERNAME = "nextcloud_server_username"
        const val NEXTCLOUD_SERVER_PASSWORD = "nextcloud_server_password"

        const val INITIAL_SYNC_COMPLETED = "initial_sync_completed"
        const val LAST_ENTRIES_SYNC_DATE_TIME = "last_entries_sync_date_time"

        const val SHOW_OPENED_ENTRIES = "show_opened_entries"

        const val SORT_ORDER = "sort_order"
        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        const val SHOW_PREVIEW_IMAGES = "show_preview_images"
        const val CROP_PREVIEW_IMAGES = "crop_preview_images"
    }
}