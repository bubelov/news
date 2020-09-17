package co.appreactor.nextcloud.news.entry

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.entries.EntriesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
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
        val instant = Instant.fromEpochSeconds(entry.pubDate)
        return DateFormat.getDateInstance(DateFormat.LONG).format(Date(instant.toEpochMilliseconds()))
    }

    suspend fun getStarredFlag(id: String) = entriesRepository.get(id).map { it?.starred == true }

    suspend fun toggleReadFlag(id: String) {
        val item = getEntry(id)!!
        entriesRepository.setUnread(id, !item.unread)
    }

    suspend fun toggleStarredFlag(id: String) {
        val item = getEntry(id)!!
        entriesRepository.setUnread(id, !item.starred)
    }

    suspend fun syncEntriesFlags() = newsApiSync.syncEntriesFlags()
}