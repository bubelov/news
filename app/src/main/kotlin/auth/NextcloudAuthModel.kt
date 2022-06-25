package auth

import androidx.lifecycle.ViewModel
import api.nextcloud.NextcloudNewsApiBuilder
import common.ConfRepository
import okhttp3.HttpUrl
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class NextcloudAuthModel(
    private val confRepo: ConfRepository,
    private val backgroundSyncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun testServerConf(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = NextcloudNewsApiBuilder().build(
            url = url.toString().trim('/'),
            username = username,
            password = password,
            trustSelfSignedCerts = trustSelfSignedCerts,
        )

        api.getFeeds()
    }

    suspend fun saveServerConf(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        confRepo.save {
            it.copy(
                backend = ConfRepository.BACKEND_NEXTCLOUD,
                nextcloudServerUrl = url.toString().trim('/'),
                nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
                nextcloudServerUsername = username,
                nextcloudServerPassword = password,
            )
        }
    }

    suspend fun scheduleBackgroundSync() {
        backgroundSyncScheduler.schedule(override = true)
    }
}