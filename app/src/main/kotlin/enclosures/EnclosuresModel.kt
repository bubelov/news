package enclosures

import androidx.lifecycle.ViewModel
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Db
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EnclosuresModel(
    private val db: Db,
    private val audioEnclosuresRepo: AudioEnclosuresRepository,
) : ViewModel() {

    fun getEnclosures(): Flow<List<Link>> {
        return db.entryQueries.selectAll().asFlow().mapToList().map { list -> list.map { it.links }.flatten() }
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        withContext(Dispatchers.Default) {
            //audioEnclosuresRepo.deleteFromCache(enclosure)
        }
    }
}