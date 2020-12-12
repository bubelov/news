package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.DirectNextcloudNewsApiBuilder
import common.*
import common.Preferences.Companion.AUTH_TYPE
import common.Preferences.Companion.NEXTCLOUD_SERVER_PASSWORD
import common.Preferences.Companion.NEXTCLOUD_SERVER_URL
import common.Preferences.Companion.NEXTCLOUD_SERVER_USERNAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType

class DirectAuthFragmentModel(
    private val nextcloudApiSwitcher: NewsApiSwitcher,
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

    fun setServer(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        prefs.putStringBlocking(NEXTCLOUD_SERVER_URL, serverUrl)
        prefs.putStringBlocking(NEXTCLOUD_SERVER_USERNAME, username)
        prefs.putStringBlocking(NEXTCLOUD_SERVER_PASSWORD, password)
    }

    fun setAuthType(authType: String) {
        prefs.putStringBlocking(AUTH_TYPE, authType)
        nextcloudApiSwitcher.switch(authType)
    }
}