package entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import feeds.FeedsRepository
import common.NewsApiSync
import db.Feed
import db.Entry
import entries.EntriesRepository
import kotlinx.coroutines.launch
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class EntryViewModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    fun getFeed(id: String): Feed? {
        return feedsRepository.selectById(id)
    }

    fun getEntry(id: String): Entry? {
        return entriesRepository.selectById(id)
    }

    fun getDate(entry: Entry): String {
        val instant = Instant.parse(entry.published)
        return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
            .format(Date(instant.millis))
    }

    fun toggleBookmarked(entryId: String) {
        val entry = getEntry(entryId) ?: return
        entriesRepository.setBookmarked(entry.id, !entry.bookmarked)

        viewModelScope.launch {
            runCatching { newsApiSync.syncEntriesFlags() }
        }
    }
}