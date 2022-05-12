package feedsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import feeds.FeedsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class FeedSettingsViewModel(
    private val feedsRepository: FeedsRepository,
) : ViewModel() {

    fun getFeed(id: String) = feedsRepository.selectById(id)

    fun setOpenEntriesInBrowser(feedId: String, openEntriesInBrowser: Boolean) {
        viewModelScope.launch {
            val feed = getFeed(feedId).first() ?: return@launch
            feedsRepository.insertOrReplace(feed.copy(openEntriesInBrowser = openEntriesInBrowser))
        }
    }

    suspend fun setShowPreviewImages(feedId: String, value: Boolean?) {
        val feed = getFeed(feedId).first() ?: return
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
        viewModelScope.launch {
            val feed = getFeed(feedId).first() ?: return@launch
            feedsRepository.insertOrReplace(feed.copy(blockedWords = blockedWords))
        }
    }
}