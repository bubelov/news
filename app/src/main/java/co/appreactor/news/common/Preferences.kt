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
        const val SERVER_URL = "server_url"
        const val SERVER_USERNAME = "server_username"
        const val SERVER_PASSWORD = "server_password"

        const val INITIAL_SYNC_COMPLETED = "initial_sync_completed"
        const val LAST_ENTRIES_SYNC_DATE_TIME = "last_entries_sync_date_time"

        const val SHOW_READ_ENTRIES = "show_read_entries"

        const val SHOW_PREVIEW_IMAGES = "show_preview_images"
        const val CROP_PREVIEW_IMAGES = "crop_preview_images"
    }
}