package feedsettings

import androidx.lifecycle.ViewModel
import feeds.FeedsRepository

class FeedSettingsViewModel(
    private val feedsRepository: FeedsRepository,
) : ViewModel() {

    fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setOpenEntriesInBrowser(feedId: String, openEntriesInBrowser: Boolean) {
        val feed = getFeed(feedId) ?: return
        feedsRepository.insertOrReplace(feed.copy(openEntriesInBrowser = openEntriesInBrowser))
    }
}