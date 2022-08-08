package entry

import android.app.Application
import android.text.Html
import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.news.R
import conf.ConfRepo
import db.Entry
import db.Link
import enclosures.EnclosuresRepo
import entries.EntriesRepo
import feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import sync.Sync

@KoinViewModel
class EntryModel(
    private val app: Application,
    private val enclosuresRepo: EnclosuresRepo,
    private val entriesRepository: EntriesRepo,
    private val feedsRepository: FeedsRepo,
    private val newsApiSync: Sync,
    confRepo: ConfRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Progress)
    val state = _state.asStateFlow()

    val conf = confRepo.conf

    private val args = MutableStateFlow<Args?>(null)

    init {
        viewModelScope.launch { enclosuresRepo.deletePartialDownloads() }

        combine(args, entriesRepository.selectCount()) { args, _ ->
            if (args == null) {
                _state.update { State.Progress }
                return@combine
            }

            runCatching {
                val entry = entriesRepository.selectById(args.entryId).first()

                if (entry == null) {
                    val message = app.getString(R.string.cannot_find_entry_with_id_s, args.entryId)
                    _state.update { State.Error(message) }
                    return@combine
                }

                val feed = feedsRepository.selectById(entry.feedId).first()

                if (feed == null) {
                    val message = app.getString(R.string.cannot_find_feed_with_id_s, entry.feedId)
                    _state.update { State.Error(message) }
                    return@combine
                }

                _state.update {
                    State.Success(
                        feedTitle = feed.title,
                        entry = entry,
                        entryLinks = entry.links,
                        parsedContent = parseEntryContent(
                            entry.contentText ?: "",
                            TextViewImageGetter(
                                textView = args.summaryView,
                                scope = args.lifecycleScope,
                                baseUrl = null,
                            ),
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update { State.Error(throwable.message ?: "") }
            }
        }.launchIn(viewModelScope)
    }

    fun setArgs(args: Args) {
        this.args.update { args }
    }

    fun setBookmarked(
        entryId: String,
        bookmarked: Boolean,
    ) {
        viewModelScope.launch {
            entriesRepository.setBookmarked(
                id = entryId,
                bookmarked = bookmarked,
                bookmarkedSynced = false,
            )

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
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

    suspend fun downloadAudioEnclosure(enclosure: Link) {
        enclosuresRepo.downloadAudioEnclosure(enclosure)
    }

    suspend fun deleteEnclosure(enclosure: Link) {
        enclosuresRepo.deleteFromCache(enclosure)
    }

    data class Args(
        val entryId: String,
        val summaryView: TextView,
        val lifecycleScope: LifecycleCoroutineScope,
    )

    sealed class State {

        object Progress : State()

        data class Success(
            val feedTitle: String,
            val entry: Entry,
            val entryLinks: List<Link>,
            val parsedContent: SpannableStringBuilder,
        ) : State()

        data class Error(val message: String) : State()
    }
}