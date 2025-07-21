package hnentries

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.HnEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class HnEntriesRepo(
    private val db: Db,
) {

    suspend fun insertOrReplace(entries: List<HnEntry>) {
        withContext(Dispatchers.IO) {
            db.entryQueries.transaction {
                entries.forEach { entry ->
                    db.hnEntryQueries.insertOrReplace(entry)
                }
            }
        }
    }

    fun deleteByFeedId(entryId: String) {
        db.hnEntryQueries.deleteByMfEntryId(entryId)
    }
    fun selectById(entryId: Long): Flow<HnEntry?> {
        return db.hnEntryQueries.selectById(entryId).asFlow().mapToOneOrNull()
    }

    fun entryExists(entryId: Long): Boolean {
        return db.hnEntryQueries.existsById(entryId).executeAsOne()
    }
}