package enclosures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.feedk.AtomLinkRel
import db.Link
import entries.EntriesRepo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class EnclosuresModel(
    private val enclosuresRepo: EnclosuresRepo,
    private val entriesRepo: EntriesRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.LoadingEnclosures)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            enclosuresRepo.deletePartialDownloads()

            entriesRepo.selectCount().collect {
                val entries = entriesRepo.selectAllLinksPublishedAndTitle().first()
                val enclosures = mutableListOf<EnclosuresAdapter.Item>()

                entries.forEach { entry ->
                    val entryEnclosures = entry.links.filter { it.rel is AtomLinkRel.Enclosure }

                    enclosures += entryEnclosures.map {
                        EnclosuresAdapter.Item(
                            entryId = it.entryId!!,
                            enclosure = it,
                            primaryText = entry.title,
                            secondaryText = DATE_TIME_FORMAT.format(entry.published),
                        )
                    }
                }

                _state.update { State.ShowingEnclosures(enclosures) }
            }
        }
    }

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        enclosuresRepo.downloadAudioEnclosure(enclosure)
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        enclosuresRepo.deleteFromCache(enclosure)
    }

    sealed class State {
        object LoadingEnclosures : State()
        data class ShowingEnclosures(val items: List<EnclosuresAdapter.Item>) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}