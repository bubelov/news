package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.miniflux.MinifluxApiBuilder
import common.ConfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MinifluxAuthViewModel(
    private val apiSwitcher: NewsApiSwitcher,
    private val conf: ConfRepository,
) : ViewModel() {

    suspend fun requestFeeds(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = MinifluxApiBuilder().build(
            serverUrl,
            username,
            password,
            trustSelfSignedCerts,
        )

        withContext(Dispatchers.IO) { api.getFeeds() }
    }

    suspend fun setServer(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val updatedConf = conf.get().copy(
            minifluxServerUrl = serverUrl,
            minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
            minifluxServerUsername = username,
            minifluxServerPassword = password,
        )

        conf.save(updatedConf)
    }

    suspend fun setAuthType(newAuthType: String) {
        val updatedConf = conf.get().copy(authType = newAuthType)
        conf.save(updatedConf)
        apiSwitcher.switch(newAuthType)
    }
}