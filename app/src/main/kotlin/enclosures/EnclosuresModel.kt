package enclosures

import androidx.lifecycle.ViewModel
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import links.LinksRepository
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EnclosuresModel(
    private val linksRepo: LinksRepository,
    private val audioEnclosuresRepo: AudioEnclosuresRepository,
) : ViewModel() {

    fun getEnclosures(): Flow<List<Link>> {
        return linksRepo.selectEnclosures()
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        withContext(Dispatchers.Default) {
            audioEnclosuresRepo.deleteFromCache(enclosure)
        }
    }
}