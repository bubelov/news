package log

import db.Log
import db.LogQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogRepository(
    private val db: LogQueries,
) {

    suspend fun insert(logEntry: Log) = withContext(Dispatchers.IO) {
        db.insert(logEntry)
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