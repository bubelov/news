package common

import org.joda.time.Instant
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
private val SHORT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

fun formatDateTime(dateTime: String): String {
    val instant = Instant.parse(dateTime)
    return SHORT.format(Date(instant.millis))
}

fun Date.toIsoString(): String = ISO.format(this)