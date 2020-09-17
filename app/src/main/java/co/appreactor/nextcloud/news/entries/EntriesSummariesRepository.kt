package co.appreactor.nextcloud.news.entries

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import kotlin.math.min

class EntriesSummariesRepository {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    private val cache = Collections.synchronizedMap(mutableMapOf<Long, String>())

    suspend fun getSummary(entry: Entry): String = withContext(Dispatchers.IO) {
        val cachedSummary = cache[entry.id]

        if (cachedSummary != null) {
            return@withContext cachedSummary
        }

        if (entry.body.isBlank()) {
            cache[entry.id] = ""
            return@withContext ""
        }

        val summary = runCatching {
            val parsedBody = HtmlCompat.fromHtml(entry.body, HtmlCompat.FROM_HTML_MODE_COMPACT)

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

        cache[entry.id] = summary
        summary
    }

    fun getCachedSummary(entryId: Long) = cache[entryId]
}