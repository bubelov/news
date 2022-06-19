package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import api.nextcloud.NextcloudNewsApiBuilder
import common.ConfRepository
import okhttp3.HttpUrl
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class NextcloudAuthModel(
    private val apiSwitcher: NewsApiSwitcher,
    private val confRepo: ConfRepository,
) : ViewModel() {

    suspend fun testServerConf(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = NextcloudNewsApiBuilder().build(
            url = url.toString(),
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
                nextcloudServerUrl = url.toString(),
                nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
                nextcloudServerUsername = username,
                nextcloudServerPassword = password,
            )
        }

        apiSwitcher.switch(ConfRepository.BACKEND_NEXTCLOUD)
    }
}