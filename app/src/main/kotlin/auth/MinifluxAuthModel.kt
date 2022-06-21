package auth

import androidx.lifecycle.ViewModel
import api.miniflux.MinifluxApiBuilder
import common.ConfRepository
import okhttp3.HttpUrl
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class MinifluxAuthModel(
    private val confRepo: ConfRepository,
) : ViewModel() {

    suspend fun testServerConf(
        url: HttpUrl,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = MinifluxApiBuilder().build(
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
                backend = ConfRepository.BACKEND_MINIFLUX,
                minifluxServerUrl = url.toString(),
                minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
                minifluxServerUsername = username,
                minifluxServerPassword = password,
            )
        }
    }
}