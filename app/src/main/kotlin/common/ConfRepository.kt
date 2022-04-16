package common

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import db.Conf
import db.ConfQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfRepository(
    private val db: ConfQueries,
) {

    fun select() = db.select().asFlow().mapToOneOrDefault(DEFAULT_CONF, Dispatchers.IO)

    suspend fun insert(conf: Conf) = withContext(Dispatchers.IO) {
        db.transaction {
            db.delete()

            db.insert(
                authType = conf.authType,
                nextcloudServerUrl = conf.nextcloudServerUrl,
                nextcloudServerTrustSelfSignedCerts = conf.nextcloudServerTrustSelfSignedCerts,
                nextcloudServerUsername = conf.nextcloudServerUsername,
                nextcloudServerPassword = conf.nextcloudServerPassword,
                minifluxServerUrl = conf.minifluxServerUrl,
                minifluxServerTrustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                minifluxServerUsername = conf.minifluxServerUsername,
                minifluxServerPassword = conf.minifluxServerPassword,
                initialSyncCompleted = conf.initialSyncCompleted,
                lastEntriesSyncDateTime = conf.lastEntriesSyncDateTime,
                showReadEntries = conf.showReadEntries,
                sortOrder = conf.sortOrder,
                showPreviewImages = conf.showPreviewImages,
                cropPreviewImages = conf.cropPreviewImages,
                markScrolledEntriesAsRead = conf.markScrolledEntriesAsRead,
                syncOnStartup = conf.syncOnStartup,
                syncInBackground = conf.syncInBackground,
                backgroundSyncIntervalMillis = conf.backgroundSyncIntervalMillis,
                useBuiltInBrowser = conf.useBuiltInBrowser,
                showPreviewText = conf.showPreviewText,
            )
        }
    }

    companion object {
        const val AUTH_TYPE_NEXTCLOUD_APP = "nextcloud_app"
        const val AUTH_TYPE_NEXTCLOUD_DIRECT = "nextcloud_direct"
        const val AUTH_TYPE_MINIFLUX = "miniflux"
        const val AUTH_TYPE_STANDALONE = "standalone"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        val DEFAULT_CONF = Conf(
            authType = "",
            nextcloudServerUrl = "",
            nextcloudServerTrustSelfSignedCerts = false,
            nextcloudServerUsername = "",
            nextcloudServerPassword = "",
            minifluxServerUrl = "",
            minifluxServerTrustSelfSignedCerts = false,
            minifluxServerUsername = "",
            minifluxServerPassword = "",
            initialSyncCompleted = false,
            lastEntriesSyncDateTime = "",
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
        )
    }
}