package co.appreactor.news.auth

import androidx.lifecycle.ViewModel
import co.appreactor.news.api.DirectApiBuilder
import co.appreactor.news.common.Preferences
import co.appreactor.news.common.setServerPassword
import co.appreactor.news.common.setServerUrl
import co.appreactor.news.common.setServerUsername
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
        return try {
            val api = DirectApiBuilder().build(
                serverUrl,
                username,
                password
            )

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
        prefs.setServerUrl(serverUrl)
        prefs.setServerUsername(username)
        prefs.setServerPassword(password)
    }
}