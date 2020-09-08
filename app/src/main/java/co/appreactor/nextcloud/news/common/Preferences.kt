package co.appreactor.nextcloud.news.common

import co.appreactor.nextcloud.news.db.Preference
import co.appreactor.nextcloud.news.db.PreferenceQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class Preferences(
    private val cache: PreferenceQueries
) {

    suspend fun getString(key: String) = withContext(Dispatchers.IO) {
        cache.findByKey(key).asFlow().map { it.executeAsOneOrNull()?.value ?: "" }
    }

    suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        cache.insertOrReplace(Preference(key, value))
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
        cache.deleteAll()
    }

    companion object {
        const val SHOW_READ_NEWS = "show_read_news"
        const val CROP_FEED_IMAGES = "crop_feed_images"

        const val SERVER_URL = "server_url"
        const val SERVER_USERNAME = "server_username"
        const val SERVER_PASSWORD = "server_password"

        const val INITIAL_SYNC_COMPLETED = "initial_sync_completed"
    }
}