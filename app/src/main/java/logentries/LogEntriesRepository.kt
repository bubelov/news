package logentries

import db.LogEntry
import db.LogEntryQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogEntriesRepository(
    private val db: LogEntryQueries,
) {

    suspend fun insert(logEntry: LogEntry) = withContext(Dispatchers.IO) {
        db.insert(logEntry)
    }

    suspend fun selectAll() = withContext(Dispatchers.IO) {
        db.selectAll().executeAsList()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
    }
}