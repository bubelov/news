package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.Preference
import co.appreactor.nextcloud.news.db.PreferenceQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PreferencesRepository(
    private val cache: PreferenceQueries
) {

    suspend fun get(key: String) = withContext(Dispatchers.IO) {
        cache.findByKey(key).asFlow().map { it.executeAsOneOrNull()?.value ?: "" }
    }

    suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        cache.insertOrReplace(Preference(key, value))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.deleteAll()
    }

    companion object {
        const val SHOW_READ_NEWS = "show_read_news"

        const val SERVER_URL = "server_url"
        const val SERVER_USERNAME = "server_username"
        const val SERVER_PASSWORD = "server_password"
    }
}