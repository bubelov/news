package co.appreactor.nextcloud.news.feeditems

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import kotlin.math.min

class FeedItemsSummariesRepository {

    companion object {
        private const val SUMMARY_MAX_LENGTH = 150
    }

    private val cache = Collections.synchronizedMap(mutableMapOf<Long, String>())

    suspend fun getSummary(feedItem: FeedItem): String = withContext(Dispatchers.IO) {
        val cachedSummary = cache[feedItem.id]

        if (cachedSummary != null) {
            return@withContext cachedSummary
        }

        if (feedItem.body.isBlank()) {
            cache[feedItem.id] = ""
            return@withContext ""
        }

        val summary = runCatching {
            val parsedBody = HtmlCompat.fromHtml(feedItem.body, HtmlCompat.FROM_HTML_MODE_COMPACT)

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

        cache[feedItem.id] = summary
        summary
    }

    fun getCachedSummary(feedItemId: Long) = cache[feedItemId]
}