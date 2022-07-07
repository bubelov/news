package auth

import androidx.lifecycle.ViewModel
import api.miniflux.MinifluxApiBuilder
import conf.ConfRepo
import okhttp3.HttpUrl
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class MinifluxAuthModel(
    private val confRepo: ConfRepo,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun testBackend(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = MinifluxApiBuilder().build(
            url = url.toString().trim('/'),
            username = username,
            password = password,
            trustSelfSignedCerts = trustSelfSignedCerts,
        )

        api.getFeeds()
    }

    fun setBackend(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                minifluxServerUrl = url.toString().trim('/'),
                minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
                minifluxServerUsername = username,
                minifluxServerPassword = password,
            )
        }

        syncScheduler.schedule(override = true)
    }
}