package co.appreactor.nextcloud.news.feeditems

import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.regex.Pattern

private const val SUMMARY_MAX_LENGTH = 150

suspend fun FeedItem.getSummary(): String = withContext(Dispatchers.IO) {
    if (body.isBlank()) {
        return@withContext ""
    }

    runCatching {
        val replaceImgPattern = Pattern.compile("<img([\\w\\W]+?)>", Pattern.DOTALL)
        val bodyWithoutImg = body.replace(replaceImgPattern.toRegex(), "")
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
}