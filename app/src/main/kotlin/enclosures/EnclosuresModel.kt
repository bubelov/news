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
        return entriesRepo.selectAll().first().map { entry ->
            entry.links
                .filter { it.rel == "enclosure" }
                .map {
                    EnclosuresAdapter.Item(
                        entryId = entry.id,
                        title = it.title,
                        url = it.href.toHttpUrl(),
                        downloaded = enclosuresRepo.selectByEntryId(entry.id).first()?.downloadPercent == 100L,
                    )
                }
        }.flatten()
    }

    suspend fun deleteEnclosure(entryId: String) {
        val enclosure = enclosuresRepo.selectByEntryId(entryId).first() ?: return
        enclosuresRepo.deleteFromCache(enclosure)
    }
}