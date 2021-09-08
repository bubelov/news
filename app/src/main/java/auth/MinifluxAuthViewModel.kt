package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.DirectNextcloudNewsApiBuilder
import common.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MinifluxAuthViewModel(
    private val apiSwitcher: NewsApiSwitcher,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    suspend fun requestFeeds(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = DirectNextcloudNewsApiBuilder().build(
            serverUrl,
            username,
            password,
            trustSelfSignedCerts,
        )

        withContext(Dispatchers.IO) {
            val response = api.getFeedsRaw().execute()

            if (!response.isSuccessful) {
                throw Exception(response.message())
            }
        }
    }

    suspend fun setServer(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        preferencesRepository.save {
            minifluxServerUrl = serverUrl
            minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts
            minifluxServerUsername = username
            minifluxServerPassword = password
        }
    }

    suspend fun setAuthType(newAuthType: String) {
        preferencesRepository.save {
            authType = newAuthType
        }

        apiSwitcher.switch(newAuthType)
    }
}