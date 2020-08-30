package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class DirectAuthFragmentModel(
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun isApiAvailable(
        serverUrl: String,
        username: String,
        password: String
    ): Boolean {
        val api = DirectApiBuilder().build(
            serverUrl,
            username,
            password
        )

        return try {
            withContext(Dispatchers.IO) {
                api.getFeeds().execute().body()!!
            }

            true
        } catch (e: Exception) {
            Timber.e(e)
            false
        }

    }

    suspend fun setServer(
        serverUrl: String,
        username: String,
        password: String
    ) {
        prefs.putString(Preferences.SERVER_URL, serverUrl)
        prefs.putString(Preferences.SERVER_USERNAME, username)
        prefs.putString(Preferences.SERVER_PASSWORD, password)
    }
}