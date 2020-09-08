package co.appreactor.nextcloud.news.newsitem

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.common.Sync
import co.appreactor.nextcloud.news.db.NewsFeed
import co.appreactor.nextcloud.news.db.NewsItem
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.text.DateFormat
import java.util.*

class NewsItemFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val sync: Sync
) : ViewModel() {

    suspend fun getNewsItem(id: Long): NewsItem {
        return newsItemsRepository.byId(id).first()!!
    }

    suspend fun getFeed(id: Long): NewsFeed {
        return newsFeedsRepository.byId(id).first()!!
    }

    fun getDate(item: NewsItem): String {
        val instant = Instant.fromEpochSeconds(item.pubDate)
        return DateFormat.getDateInstance(DateFormat.LONG).format(Date(instant.toEpochMilliseconds()))
    }

    suspend fun getReadFlag(id: Long) = newsItemsRepository.byId(id).map { it?.unread == false }

    suspend fun getStarredFlag(id: Long) = newsItemsRepository.byId(id).map { it?.starred == true }

    suspend fun toggleReadFlag(id: Long) {
        val item = getNewsItem(id)
        newsItemsRepository.updateUnread(id, !item.unread)
    }

    suspend fun toggleStarredFlag(id: Long) {
        val item = getNewsItem(id)
        newsItemsRepository.updateStarred(id, !item.starred)
    }

    fun syncNewsFlags() {
        GlobalScope.launch {
            runCatching {
                sync.syncNewsFlagsOnly()
            }
        }
    }
}