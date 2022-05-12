package db

import com.squareup.sqldelight.ColumnAdapter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime

fun entryAdapter() = Entry.Adapter(
    publishedAdapter = offsetDateTimeAdapter(),
    updatedAdapter = offsetDateTimeAdapter(),
)

fun linkAdapter() = Link.Adapter(
    hrefAdapter = httpUrlAdapter(),
)

private fun offsetDateTimeAdapter() = object : ColumnAdapter<OffsetDateTime, String> {
    override fun decode(databaseValue: String): OffsetDateTime {
        try {
            val value = OffsetDateTime.parse(databaseValue)

            if (value != null) {
                return value
            } else {
                throw IllegalArgumentException("Invalid date format: $databaseValue")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format: $databaseValue")
        }
    }

    override fun encode(value: OffsetDateTime) = value.toString()
}

private fun httpUrlAdapter() = object : ColumnAdapter<HttpUrl, String> {
    override fun decode(databaseValue: String): HttpUrl = databaseValue.toHttpUrl()
    override fun encode(value: HttpUrl) = value.toString()
}