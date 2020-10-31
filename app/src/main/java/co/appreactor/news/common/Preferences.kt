package co.appreactor.news.common

import co.appreactor.news.db.Preference
import co.appreactor.news.db.PreferenceQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class Preferences(
    private val db: PreferenceQueries
) {

    suspend fun getString(key: String) = withContext(Dispatchers.IO) {
        db.selectByKey(key).asFlow().map { it.executeAsOneOrNull()?.value ?: "" }
    }

    suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        db.insertOrReplace(Preference(key, value))
    }

    suspend fun getBoolean(key: String, default: Boolean) = getString(key).map {
        when (it) {
            "" -> default
            "true" -> true
            "false" -> false
            else -> throw Exception("Unsupported value: $it")
        }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        putString(key, if (value) "true" else "false")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.deleteAll()
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

        const val SHOW_PREVIEW_IMAGES = "show_preview_images"
        const val CROP_PREVIEW_IMAGES = "crop_preview_images"
    }
}