package co.appreactor.nextcloud.news.logging

import co.appreactor.nextcloud.news.db.LoggedException
import co.appreactor.nextcloud.news.db.LoggedExceptionQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExceptionsRepository(
    private val cache: LoggedExceptionQueries
) {

    suspend fun add(exception: LoggedException) = withContext(Dispatchers.IO) {
        cache.insertOrReplace(exception)
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        cache.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun count() = withContext(Dispatchers.IO) {
        cache.count().asFlow().map { it.executeAsOne() }
    }
}