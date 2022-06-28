package conf

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import db.Conf
import db.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class ConfRepository(
    private val db: Db,
) {

    companion object {
        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"
        const val BACKEND_NEXTCLOUD = "nextcloud"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        val DEFAULT_CONF = Conf(
            backend = "",
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

    fun load(): Flow<Conf> {
        return db.confQueries.selectAll().asFlow().mapToOneOrDefault(DEFAULT_CONF)
    }

    suspend fun save(newConf: (Conf) -> Conf) {
        save(newConf(db.confQueries.selectAll().executeAsOneOrNull() ?: DEFAULT_CONF))
    }

    private suspend fun save(conf: Conf) {
        withContext(Dispatchers.Default) {
            db.transaction {
                db.confQueries.deleteAll()
                db.confQueries.insert(conf)
            }
        }
    }
}