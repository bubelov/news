package org.vestifeed.auth

import androidx.lifecycle.ViewModel
import org.vestifeed.api.nextcloud.NextcloudApiBuilder
import okhttp3.HttpUrl
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.Db
import org.vestifeed.sync.BackgroundSyncScheduler

class NextcloudAuthModel(
    private val db: Db,
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
        db.confQueries.update {
            it.copy(
                backend = ConfQueries.BACKEND_NEXTCLOUD,
                nextcloudServerUrl = url.toString().trim('/'),
                nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
                nextcloudServerUsername = username,
                nextcloudServerPassword = password,
            )
        }

        syncScheduler.schedule()
    }
}