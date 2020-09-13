package co.appreactor.nextcloud.news.feeditem

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.db.Feed
import co.appreactor.nextcloud.news.db.FeedItem
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import java.text.DateFormat
import java.util.*

class FeedItemFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val databaseSyncManager: NewsApiSync,
) : ViewModel() {

    suspend fun getFeedItem(id: Long): FeedItem? {
        return feedItemsRepository.byId(id).first()
    }

    suspend fun getFeed(id: Long): Feed? {
        return feedsRepository.byId(id).first()
    }

    fun getDate(item: FeedItem): String {
        val instant = Instant.fromEpochSeconds(item.pubDate)
        return DateFormat.getDateInstance(DateFormat.LONG).format(Date(instant.toEpochMilliseconds()))
    }

    suspend fun getStarredFlag(id: Long) = feedItemsRepository.byId(id).map { it?.starred == true }

    suspend fun toggleReadFlag(id: Long) {
        val item = getFeedItem(id)!!
        feedItemsRepository.updateUnread(id, !item.unread)
    }

    suspend fun toggleStarredFlag(id: Long) {
        val item = getFeedItem(id)!!
        feedItemsRepository.updateStarred(id, !item.starred)
    }

    suspend fun syncFeedItemsFlags() {
        databaseSyncManager.syncNewsFlagsOnly()
    }
}