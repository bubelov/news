package log

import db.LogQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.OffsetDateTime

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

    suspend fun deleteOlderThan(duration: Duration) = withContext(Dispatchers.IO) {
        Timber.d("Deleting old log rows")
        val now = OffsetDateTime.now()
        val threshold = now.minus(duration)
        Timber.d("Now: $now, duration: $duration, threshold: $threshold")
        val rowsBeforeDeletion = db.selectCount().executeAsOne()
        Timber.d("Rows before deletion: $rowsBeforeDeletion")
        db.deleteWhereDateLessThan(threshold.toString())
        val rowsAfterDeletion = db.selectCount().executeAsOne()
        Timber.d("Rows after deletion: $rowsAfterDeletion")
        Timber.d("Deleted approximately ${rowsBeforeDeletion - rowsAfterDeletion} rows")
    }
}