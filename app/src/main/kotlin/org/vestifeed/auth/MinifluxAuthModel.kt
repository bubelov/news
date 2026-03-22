package org.vestifeed.auth

import androidx.lifecycle.ViewModel
import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.conf.ConfRepo
import okhttp3.HttpUrl
import org.vestifeed.db.ConfQueries
import org.vestifeed.sync.BackgroundSyncScheduler

class MinifluxAuthModel(
    private val confRepo: ConfRepo,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun testBackend(
        url: HttpUrl,
        token: String,
        trustSelfSignedCerts: Boolean,
    ) {
        val api = MinifluxApiBuilder().build(
            url = url.toString().trim('/'),
            token = token,
            trustSelfSignedCerts = trustSelfSignedCerts,
        )

        api.getFeeds()
    }

    fun setBackend(
        url: HttpUrl,
        token: String,
        trustSelfSignedCerts: Boolean,
    ) {
        confRepo.update {
            it.copy(
                backend = ConfQueries.BACKEND_MINIFLUX,
                minifluxServerUrl = url.toString().trim('/'),
                minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
                minifluxServerToken = token,
            )
        }

        syncScheduler.schedule()
    }
}
