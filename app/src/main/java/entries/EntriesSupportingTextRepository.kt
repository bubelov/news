package entries

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import db.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import kotlin.math.min

class EntriesSupportingTextRepository(
    private val entriesRepository: EntriesRepository,
) {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    private val cache = Collections.synchronizedMap(mutableMapOf<String, String>())

    suspend fun getSupportingText(entryId: String, feed: Feed?): String = withContext(Dispatchers.IO) {
        val cachedSummary = cache[entryId]

        if (cachedSummary != null) {
            return@withContext cachedSummary
        }

        val entry = entriesRepository.get(entryId).first()

        if (entry == null || entry.content.isBlank()) {
            cache[entryId] = ""
            return@withContext ""
        }

        val summary = runCatching {
            if (feed?.selfLink?.startsWith("https://news.ycombinator.com") == true) {
                return@runCatching ""
            }

            val parsedBody = HtmlCompat.fromHtml(entry.content, HtmlCompat.FROM_HTML_MODE_COMPACT)

            if (parsedBody is SpannableStringBuilder) {
                parsedBody.clearSpans()
            }

            buildString {
                val untrimmedBeginning = parsedBody.substring(
                    0,
                    min(parsedBody.length - 1, SUMMARY_MAX_LENGTH)
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

    fun getCachedSupportingText(entryId: String) = cache[entryId]
}