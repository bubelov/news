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

    fun setShowPreviewImages(feedId: String, value: Boolean?) {
        val feed = getFeed(feedId) ?: return
        feedsRepository.insertOrReplace(feed.copy(showPreviewImages = value))
    }

    fun formatBlockedWords(blockedWords: String): String {
        val separatedWords = blockedWords.split(",")

        return if (separatedWords.isEmpty()) {
            ""
        } else {
            buildString {
                separatedWords.forEach {
                    append(it.trim())
                    append(",")
                }
            }.dropLast(1)
        }
    }

    fun setBlockedWords(feedId: String, blockedWords: String) {
        val feed = getFeed(feedId) ?: return
        feedsRepository.insertOrReplace(feed.copy(blockedWords = blockedWords))
    }
}