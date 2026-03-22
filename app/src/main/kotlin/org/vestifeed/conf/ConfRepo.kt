package org.vestifeed.conf

import org.vestifeed.db.Conf
import org.vestifeed.db.Db
import kotlinx.coroutines.flow.StateFlow

class ConfRepo(
    private val db: Db,
) {

    val conf: StateFlow<Conf> = db.confQueries.conf

    fun update(newConf: (Conf) -> Conf) {
        db.confQueries.update(newConf)
    }

    companion object {
        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"
        const val BACKEND_NEXTCLOUD = "nextcloud"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        val DEFAULT_CONF = Conf(
            backend = "",
            minifluxServerUrl = "",
            minifluxServerTrustSelfSignedCerts = false,
            minifluxServerToken = "",
            nextcloudServerUrl = "",
            nextcloudServerTrustSelfSignedCerts = false,
            nextcloudServerUsername = "",
            nextcloudServerPassword = "",
            initialSyncCompleted = false,
            lastEntriesSyncDatetime = "",
            showReadEntries = false,
            sortOrder = SORT_ORDER_DESCENDING,
            showPreviewImages = true,
            cropPreviewImages = true,
            markScrolledEntriesAsRead = false,
            syncOnStartup = true,
            syncInBackground = true,
            backgroundSyncIntervalMillis = 10800000,
            useBuiltInBrowser = true,
            showPreviewText = true,
            syncedOnStartup = false,
        )
    }
}
