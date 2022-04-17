package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.miniflux.MinifluxApiBuilder
import common.ConfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MinifluxAuthViewModel(
    private val apiSwitcher: NewsApiSwitcher,
    private val confRepo: ConfRepository,
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
        val newConf = confRepo.select().first().copy(
            minifluxServerUrl = serverUrl,
            minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
            minifluxServerUsername = username,
            minifluxServerPassword = password,
        )

        confRepo.upsert(newConf)
    }

    suspend fun setAuthType(newAuthType: String) {
        val newConf = confRepo.select().first().copy(authType = newAuthType)
        confRepo.upsert(newConf)
        apiSwitcher.switch(newAuthType)
    }
}