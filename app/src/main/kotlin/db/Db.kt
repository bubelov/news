package db

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.squareup.sqldelight.ColumnAdapter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime

fun entryAdapter() = Entry.Adapter(
    publishedAdapter = offsetDateTimeAdapter(),
    updatedAdapter = offsetDateTimeAdapter(),
    linksAdapter = linksAdapter(),
)

fun feedAdapter() = Feed.Adapter(
    linksAdapter = linksAdapter(),
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

private fun linksAdapter() = object : ColumnAdapter<List<Link>, String> {

    private val gson = GsonBuilder().registerTypeAdapter(HttpUrl::class.java, object: TypeAdapter<HttpUrl>() {
        override fun write(out: JsonWriter, value: HttpUrl?) {
            out.value(value.toString())
        }

        override fun read(`in`: JsonReader): HttpUrl {
            return `in`.nextString().toHttpUrl()
        }
    }).create()

    override fun decode(databaseValue: String): List<Link> {
        return if (databaseValue.isEmpty()) {
            emptyList()
        } else {
            gson.fromJson(databaseValue, Array<Link>::class.java).asList()
        }
    }

    override fun encode(value: List<Link>) = gson.toJson(value)
}