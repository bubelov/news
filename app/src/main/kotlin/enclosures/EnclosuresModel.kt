package enclosures

import androidx.lifecycle.ViewModel
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Db
import db.Link
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class EnclosuresModel(
    private val db: Db,
    private val enclosuresRepo: EnclosuresRepo,
) : ViewModel() {

    fun getEnclosures(): Flow<List<EnclosuresAdapter.Item>> {
        return db.entryQueries.selectAll().asFlow().mapToList().map { entries ->
            entries.map { entry ->
                entry.links.filter { it.rel == "enclosure" }.map {
                    EnclosuresAdapter.Item(
                        entryId = entry.id,
                        enclosure = it,
                        primaryText = entry.title,
                        secondaryText = DATE_TIME_FORMAT.format(entry.published),
                    )
                }
            }.flatten()
        }
    }

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        enclosuresRepo.downloadAudioEnclosure(enclosure)
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        enclosuresRepo.deleteFromCache(enclosure)
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}