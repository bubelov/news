package entries

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import db.EntryQueries
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Collections
import kotlin.math.min

class EntriesSummaryRepository(
    private val entryQueries: EntryQueries,
) {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    private val cache = Collections.synchronizedMap(mutableMapOf<String, String>())

    suspend fun getSummary(entryId: String, feed: Feed?): String =
        withContext(Dispatchers.Default) {
            val cachedSummary = cache[entryId]

            if (cachedSummary != null) {
                return@withContext cachedSummary
            }

            val entry = entryQueries.selectById(entryId).executeAsOneOrNull()

            if (entry == null || entry.contentText.isBlank()) {
                cache[entryId] = ""
                return@withContext ""
            }

            val summary = runCatching {
                if (feed?.selfLink?.startsWith("https://news.ycombinator.com") == true) {
                    return@runCatching ""
                }

                val parsedBody =
                    HtmlCompat.fromHtml(entry.contentText, HtmlCompat.FROM_HTML_MODE_COMPACT)

                if (parsedBody is SpannableStringBuilder) {
                    parsedBody.clearSpans()
                }

                buildString {
                    val untrimmedBeginning = parsedBody.substring(
                        0,
                        min(parsedBody.length, SUMMARY_MAX_LENGTH)
                    )

                    append(untrimmedBeginning.replace("\uFFFC", "").trim())

                    if (untrimmedBeginning.length == SUMMARY_MAX_LENGTH) {
                        append("…")
                    }
                }.replace("\n", " ")
            }.onFailure {
                Timber.e(it)
            }.getOrDefault("")

            cache[entryId] = summary
            summary
        }
}