package feedsettings

import androidx.lifecycle.ViewModel
import feeds.FeedsRepository

class FeedSettingsViewModel(
    private val feedsRepository: FeedsRepository,
) : ViewModel() {

    suspend fun getFeedTitle(feedId: String): String {
        return feedsRepository.selectById(feedId)!!.title
    }

    suspend fun isOpenEntriesInBrowser(feedId: String): Boolean {
        return feedsRepository.selectById(feedId)!!.openEntriesInBrowser
    }

    suspend fun setOpenEntriesInBrowser(feedId: String, openEntriesInBrowser: Boolean) {
        val feed = feedsRepository.selectById(feedId)!!
        feedsRepository.insertOrReplace(feed.copy(openEntriesInBrowser = openEntriesInBrowser))
    }
}