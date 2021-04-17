package entry

import android.app.Application
import android.text.Html
import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.news.R
import feeds.FeedsRepository
import sync.NewsApiSync
import db.Entry
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sync.SyncResult
import timber.log.Timber
import java.util.*

class EntryViewModel(
    private val app: Application,
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    val state = MutableStateFlow<State?>(null)

    suspend fun onViewCreated(
        entryId: String,
        imageGetter: Html.ImageGetter
    ) = withContext(Dispatchers.IO) {
        if (state.value != null) {
            return@withContext
        }

        state.value = State.Progress

        runCatching {
            val entry = entriesRepository.selectById(entryId)

            if (entry == null) {
                val message = app.getString(R.string.cannot_find_entry_with_id_s, entryId)
                state.value = State.Error(message)
                return@withContext
            }

            val feed = feedsRepository.selectById(entry.feedId)

            if (feed == null) {
                val message = app.getString(R.string.cannot_find_feed_with_id_s, entry.feedId)
                state.value = State.Error(message)
                return@withContext
            }

            state.value = State.Success(
                feedTitle = feed.title,
                entry = entry,
                parsedContent = parseEntryContent(entry.content, imageGetter),
            )
        }.onFailure {
            state.value = State.Error(it.message ?: "")
        }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        val prevState = state.value

        if (prevState is State.Success) {
            entriesRepository.setBookmarked(entryId, bookmarked)
            state.value = prevState.copy(entry = prevState.entry.copy(bookmarked = bookmarked))
        }

        viewModelScope.launch {
            when (val r = newsApiSync.syncEntriesFlags()) {
                is SyncResult.Err -> Timber.e(r.e)
            }
        }
    }

    private fun parseEntryContent(
        content: String,
        imageGetter: Html.ImageGetter,
    ): SpannableStringBuilder {
        val summary = HtmlCompat.fromHtml(
            content,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            imageGetter,
            null,
        ) as SpannableStringBuilder

        if (summary.isBlank()) {
            return summary
        }

        while (summary.contains("\u00A0")) {
            val index = summary.indexOfFirst { it == '\u00A0' }
            summary.delete(index, index + 1)
        }

        while (summary.contains("\n\n\n")) {
            val index = summary.indexOf("\n\n\n")
            summary.delete(index, index + 1)
        }

        while (summary.startsWith("\n\n")) {
            summary.delete(0, 1)
        }

        while (summary.endsWith("\n\n")) {
            summary.delete(summary.length - 2, summary.length - 1)
        }

        return summary
    }

    sealed class State {

        object Progress : State()

        data class Success(
            val feedTitle: String,
            val entry: Entry,
            val parsedContent: SpannableStringBuilder,
        ) : State()

        data class Error(val message: String) : State()
    }
}