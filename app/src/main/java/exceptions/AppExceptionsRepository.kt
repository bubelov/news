package exceptions

import db.LoggedException
import db.LoggedExceptionQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppExceptionsRepository(
    private val db: LoggedExceptionQueries,
) {

    suspend fun insert(item: LoggedException) = withContext(Dispatchers.IO) {
        db.insert(item)
    }

    suspend fun selectAll() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList()
    }

    suspend fun selectById(id: String) = withContext(Dispatchers.IO) {
        db.selectById(id).executeAsOneOrNull()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }
}