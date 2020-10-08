package co.appreactor.news.auth

import androidx.lifecycle.ViewModel
import co.appreactor.news.api.DirectNextcloudNewsApiBuilder
import co.appreactor.news.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType

class DirectAuthFragmentModel(
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun requestFeeds(
        serverUrl: String,
        username: String,
        password: String
    ) {
        val api = DirectNextcloudNewsApiBuilder().build(
            serverUrl,
            username,
            password
        )

        withContext(Dispatchers.IO) {
            val response = api.getFeedsRaw().execute()

            if (!response.isSuccessful) {
                throw Exception(response.message())
            } else {
                if (response.body()?.contentType() == "text/html; charset=UTF-8".toMediaType()) {
                    throw Exception("Can not fetch data. Make sure News app is also installed on your Nextcloud server.")
                }
            }
        }
    }

    suspend fun setServer(
        serverUrl: String,
        username: String,
        password: String
    ) {
        prefs.setNextcloudServerUrl(serverUrl)
        prefs.setNextcloudServerUsername(username)
        prefs.setNextcloudServerPassword(password)
    }

    suspend fun setAuthType(authType: String) = prefs.setAuthType(authType)
}