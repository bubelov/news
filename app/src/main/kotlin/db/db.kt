package db

import com.google.gson.Gson
import com.squareup.sqldelight.ColumnAdapter
import java.time.OffsetDateTime

fun entryAdapter() = Entry.Adapter(
    linksAdapter(),
    offsetDateTimeAdapter(),
    offsetDateTimeAdapter(),
)

private fun linksAdapter() = object : ColumnAdapter<List<Link>, String> {
    override fun decode(databaseValue: String): List<Link> {
        return Gson().fromJson(databaseValue, Array<Link>::class.java).toList()
    }

    override fun encode(value: List<Link>) = Gson().toJson(value)
}

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