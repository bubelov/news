package common

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import db.Conf
import db.ConfQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ConfRepository(
    private val db: ConfQueries,
) {

    suspend fun getAsFlow(): Flow<Conf> = withContext(Dispatchers.IO) {
        if (db.select().executeAsOneOrNull() == null) {
            db.insertDefault()
        }

        db.select().asFlow().mapToOne(Dispatchers.IO)
    }

    suspend fun get() = withContext(Dispatchers.IO) {
        if (db.select().executeAsOneOrNull() == null) {
            db.insertDefault()
        }

        db.select().executeAsOne()
    }

    suspend fun save(conf: Conf) = withContext(Dispatchers.IO) {
        db.insertOrReplace(conf)
    }

    companion object {
        const val AUTH_TYPE_NEXTCLOUD_APP = "nextcloud_app"
        const val AUTH_TYPE_NEXTCLOUD_DIRECT = "nextcloud_direct"
        const val AUTH_TYPE_MINIFLUX = "miniflux"
        const val AUTH_TYPE_STANDALONE = "standalone"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"
    }
}