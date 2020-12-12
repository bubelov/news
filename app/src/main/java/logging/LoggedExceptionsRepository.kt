package logging

import db.LoggedException
import db.LoggedExceptionQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoggedExceptionsRepository(
    private val db: LoggedExceptionQueries
) {

    suspend fun add(exception: LoggedException) = withContext(Dispatchers.IO) {
        db.insertOrReplace(exception)
    }

    suspend fun all() = withContext(Dispatchers.IO) {
        db.selectAll().asFlow().mapToList()
    }

    suspend fun count() = withContext(Dispatchers.IO) {
        db.selectCount().asFlow().mapToOne()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }
}