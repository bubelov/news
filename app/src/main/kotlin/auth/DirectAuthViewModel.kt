package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.DirectNextcloudNewsApiBuilder
import common.ConfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType

class DirectAuthViewModel(
    private val nextcloudApiSwitcher: NewsApiSwitcher,
    private val confRepo: ConfRepository,
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
        trustSelfSignedCerts: Boolean,
    ) {
        val newConf = confRepo.select().first().copy(
            nextcloudServerUrl = serverUrl,
            nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
            nextcloudServerUsername = username,
            nextcloudServerPassword = password,
        )

        confRepo.insert(newConf)
    }

    suspend fun setAuthType(newAuthType: String) {
        val newConf = confRepo.select().first().copy(authType = newAuthType)
        confRepo.insert(newConf)
        nextcloudApiSwitcher.switch(newAuthType)
    }
}