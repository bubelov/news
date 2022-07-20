package feedsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import db.Feed
import feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class FeedSettingsModel(
    private val feedsRepo: FeedsRepo,
) : ViewModel() {

    val feedId = MutableStateFlow("")

    private val _state = MutableStateFlow<State>(State.LoadingFeed)
    val state = _state.asStateFlow()

    init {
        feedId.onEach {
            if (it.isBlank()) {
                _state.update { State.LoadingFeed }
                return@onEach
            }

            val feed = feedsRepo.selectById(it).first()!!
            _state.update { State.ShowingFeedSettings(feed) }
        }.launchIn(viewModelScope)
    }

    suspend fun setOpenEntriesInBrowser(feedId: String, openEntriesInBrowser: Boolean) {
        val feed = feedsRepo.selectById(feedId).first()!!
        feedsRepo.insertOrReplace(feed.copy(openEntriesInBrowser = openEntriesInBrowser))
    }

    suspend fun setShowPreviewImages(feedId: String, value: Boolean?) {
        val feed = feedsRepo.selectById(feedId).first()!!
        feedsRepo.insertOrReplace(feed.copy(showPreviewImages = value))
    }

    suspend fun setBlockedWords(feedId: String, blockedWords: String) {
        val feed = feedsRepo.selectById(feedId).first()!!
        feedsRepo.insertOrReplace(feed.copy(blockedWords = blockedWords))
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

    sealed class State {
        object LoadingFeed : State()
        data class ShowingFeedSettings(val feed: Feed) : State()
    }
}