package enclosures

import androidx.lifecycle.ViewModel
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Db
import db.EntryWithoutContent
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EnclosuresModel(
    private val db: Db,
    private val audioEnclosuresRepo: AudioEnclosuresRepo,
) : ViewModel() {

    fun getEnclosures(): Flow<List<EnclosuresAdapter.Item>> {
        return db.entryQueries.selectAll()
            .asFlow()
            .mapToList()
            .map { entries ->
                entries.map { entry ->
                    entry.links
                        .filter { it.rel == "enclosure" }
                        .map {
                            EnclosuresAdapter.Item(
                                entry = entry,
                                enclosure = it,
                            )
                        }
                }.flatten()
            }
    }

    suspend fun deleteEnclosure(entry: EntryWithoutContent, enclosure: Link) {
        withContext(Dispatchers.Default) {
            audioEnclosuresRepo.deleteFromCache(entry, enclosure)
        }
    }
}