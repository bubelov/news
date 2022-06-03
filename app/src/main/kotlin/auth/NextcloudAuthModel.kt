package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.NextcloudNewsApiBuilder
import common.ConfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class NextcloudAuthModel(
    private val nextcloudApiSwitcher: NewsApiSwitcher,
    private val confRepo: ConfRepository,
) : ViewModel() {

    suspend fun requestFeeds(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = NextcloudNewsApiBuilder().build(
            serverUrl,
            username,
            password,
            trustSelfSignedCerts,
        )

        withContext(Dispatchers.Default) { api.getFeeds() }
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

        confRepo.upsert(newConf)
    }

    suspend fun setBackend(newBackend: String) {
        val newConf = confRepo.select().first().copy(backend = newBackend)
        confRepo.upsert(newConf)
        nextcloudApiSwitcher.switch(newBackend)
    }
}