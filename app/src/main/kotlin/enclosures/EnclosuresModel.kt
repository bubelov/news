package enclosures

import androidx.lifecycle.ViewModel
import entries.EntriesRepository
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl

class EnclosuresModel(
    private val entriesRepo: EntriesRepository,
    private val enclosuresRepo: EnclosuresRepository,
) : ViewModel() {

    suspend fun getEnclosures(): List<EnclosuresAdapter.Item> {
        val entriesWithEnclosures = entriesRepo.selectAll().first().filter { it.enclosureLink.isNotBlank() }

        return entriesWithEnclosures.map {
            EnclosuresAdapter.Item(
                entryId = it.id,
                title = it.title,
                url = it.enclosureLink.toHttpUrl(),
                downloaded = enclosuresRepo.selectByEntryId(it.id).first()?.downloadPercent == 100L,
            )
        }
    }

    suspend fun deleteEnclosure(entryId: String) {
        val enclosure = enclosuresRepo.selectByEntryId(entryId).first() ?: return
        enclosuresRepo.deleteFromCache(enclosure)
    }
}