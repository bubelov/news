package common

import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

private val FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

fun formatDateTime(dateTime: String): String {
    val instant = Instant.parse(dateTime)
    return FORMAT.format(Date(instant.millis))
}