package co.appreactor.nextcloud.news.logging

import co.appreactor.nextcloud.news.db.LoggedException
import co.appreactor.nextcloud.news.db.LoggedExceptionQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LoggedExceptionsRepository(
    private val db: LoggedExceptionQueries
) {

    suspend fun add(exception: LoggedException) = withContext(Dispatchers.IO) {
        db.insertOrReplace(exception)
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        db.findAll().asFlow().map { it.executeAsList() }
    }

    suspend fun count() = withContext(Dispatchers.IO) {
        db.count().asFlow().map { it.executeAsOne() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }
}