package db

import com.squareup.sqldelight.ColumnAdapter
import java.time.OffsetDateTime

fun entryAdapter() = Entry.Adapter(
    offsetDateTimeAdapter(),
    offsetDateTimeAdapter(),
)

private fun offsetDateTimeAdapter() = object : ColumnAdapter<OffsetDateTime, String> {
    override fun decode(databaseValue: String) = OffsetDateTime.parse(databaseValue)
    override fun encode(value: OffsetDateTime) = value.toString()
}