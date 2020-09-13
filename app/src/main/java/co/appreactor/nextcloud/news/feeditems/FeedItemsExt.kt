package co.appreactor.nextcloud.news.feeditems

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.text.HtmlCompat
import co.appreactor.nextcloud.news.db.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.random.Random

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

fun Bitmap.hasTransparentAngles(): Boolean {
    if (width == 0 || height == 0) {
        return false
    }

    if (getPixel(0, 0) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(width - 1, 0) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(0, height - 1) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(width - 1, height - 1) == Color.TRANSPARENT) {
        return true
    }

    return false
}

fun Bitmap.looksLikeSingleColor(): Boolean {
    if (width == 0 || height == 0) {
        return false
    }

    val randomPixels = (1..100).map {
        getPixel(Random.nextInt(0, width), Random.nextInt(0, height))
    }

    return randomPixels.all { it == randomPixels.first() }
}