package enclosures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.feedk.AtomLinkRel
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Db
import db.Entry
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@KoinViewModel
class EnclosuresModel(
    private val db: Db,
    private val enclosuresRepo: EnclosuresRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.LoadingEnclosures)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            enclosuresRepo.deletePartialDownloads()

            db.entryQueries.selectLinks()
                .asFlow()
                .mapToList()
                .map { list -> list.flatten().filter { it.rel is AtomLinkRel.Enclosure } }
                .collectLatest { onLoadEnclosures(it) }
        }
    }

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        enclosuresRepo.downloadAudioEnclosure(enclosure)
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        enclosuresRepo.deleteFromCache(enclosure)
    }

    private suspend fun onLoadEnclosures(enclosures: List<Link>) {
        withContext(Dispatchers.Default) {
            db.transaction {
                _state.update {
                    State.ShowingEnclosures(
                        enclosures
                            .map { Pair(it, db.entryQueries.selectById(it.entryId!!).executeAsOne()) }
                            .sortedByDescending { it.second.published }
                            .map { item(it.first, it.second) }
                    )
                }
            }
        }
    }

    private fun item(enclosure: Link, entry: Entry): EnclosuresAdapter.Item {
        return EnclosuresAdapter.Item(
            entryId = entry.id,
            enclosure = enclosure,
            primaryText = entry.title,
            secondaryText = DATE_TIME_FORMAT.format(entry.published),
        )
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