package co.appreactor.nextcloud.news.feeditems

import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

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
            val replaceImgPattern = Pattern.compile("<img([\\w\\W]+?)>", Pattern.DOTALL)
            val bodyWithoutImg = feedItem.body.replace(replaceImgPattern.toRegex(), "")
            val parsedBody =
                HtmlCompat.fromHtml(bodyWithoutImg, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().replace("\n", " ")

            buildString {
                append(
                    parsedBody.substring(
                        0,
                        kotlin.math.min(parsedBody.length - 1, SUMMARY_MAX_LENGTH)
                    )
                )

                if (length == SUMMARY_MAX_LENGTH) {
                    append("…")
                }
            }
        }.onFailure {
            Timber.e(it)
        }.getOrNull() ?: ""

        cache[feedItem.id] = summary
        summary
    }

    fun getCachedSummary(feedItemId: Long) = cache[feedItemId]
}