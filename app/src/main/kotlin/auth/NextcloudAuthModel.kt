package auth

import androidx.lifecycle.ViewModel
import api.nextcloud.NextcloudApiBuilder
import conf.ConfRepo
import okhttp3.HttpUrl
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class NextcloudAuthModel(
    private val confRepo: ConfRepo,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun testBackend(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = NextcloudApiBuilder().build(
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
                backend = ConfRepo.BACKEND_NEXTCLOUD,
                nextcloudServerUrl = url.toString().trim('/'),
                nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
                nextcloudServerUsername = username,
                nextcloudServerPassword = password,
            )
        }

        syncScheduler.schedule()
    }
}