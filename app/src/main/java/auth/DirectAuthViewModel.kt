package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.DirectNextcloudNewsApiBuilder
import common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType

class DirectAuthViewModel(
    private val nextcloudApiSwitcher: NewsApiSwitcher,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    suspend fun requestFeeds(
        serverUrl: String,
        username: String,
        password: String,
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
        password: String,
    ) {
        preferencesRepository.save {
            nextcloudServerUrl = serverUrl
            nextcloudServerUsername = username
            nextcloudServerPassword = password
        }
    }

    suspend fun setAuthType(newAuthType: String) {
        preferencesRepository.save {
            authType = newAuthType
        }

        nextcloudApiSwitcher.switch(newAuthType)
    }
}