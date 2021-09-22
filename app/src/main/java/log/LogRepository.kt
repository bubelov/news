package log

import db.LogQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogRepository(
    private val db: LogQueries,
) {

    suspend fun insert(
        date: String,
        level: Long,
        tag: String,
        message: String,
        stackTrace: String,
    ) = withContext(Dispatchers.IO) {
        db.insert(
            date = date,
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace,
        )
    }

    suspend fun selectAll() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList()
    }

    suspend fun selectById(id: Long) = withContext(Dispatchers.IO) {
        db.selectById(id).executeAsOneOrNull()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }
}