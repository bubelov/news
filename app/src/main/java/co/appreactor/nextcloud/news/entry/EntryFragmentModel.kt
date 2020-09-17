package co.appreactor.nextcloud.news.entry

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.entries.EntriesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.text.DateFormat
import java.util.*

class EntryFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    suspend fun getEntry(id: String): Entry? {
        return entriesRepository.get(id).first()
    }

    suspend fun getFeed(id: String): Feed? {
        return feedsRepository.get(id).first()
    }

    fun getDate(entry: Entry): String {
        val instant = LocalDateTime.parse(entry.published).toInstant(TimeZone.currentSystemDefault())
        return DateFormat.getDateInstance(DateFormat.LONG).format(Date(instant.toEpochMilliseconds()))
    }

    suspend fun getStarredFlag(id: String) = entriesRepository.get(id).map { it?.bookmarked == true }

    suspend fun toggleViewedFlag(entryId: String) {
        val entry = getEntry(entryId)

        if (entry != null) {
            entriesRepository.setViewed(entryId, !entry.viewed)
        }
    }

    suspend fun toggleStarredFlag(id: String) {
        val item = getEntry(id)!!
        entriesRepository.setBookmarked(id, !item.bookmarked)
    }

    suspend fun syncEntriesFlags() = newsApiSync.syncEntriesFlags()
}