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
        val now = OffsetDateTime.now()
        val threshold = now.minus(duration)
        val rowsBeforeDeletion = db.selectCount().executeAsOne()
        Timber.d(
            "Preparing to delete old log rows (now = %s, duration = %s, threshold = %s, rowsBeforeDeletion = %s",
            now,
            duration,
            threshold,
            rowsBeforeDeletion,
        )
        db.deleteWhereDateLessThan(threshold.toString())
        val rowsAfterDeletion = db.selectCount().executeAsOne()
        val rowsDeleted = rowsBeforeDeletion - rowsAfterDeletion
        Timber.d(
            "Deleted old log rows (rowsAfterDeletion = %s, rowsDeleted = %s)",
            rowsAfterDeletion,
            rowsDeleted,
        )
    }
}