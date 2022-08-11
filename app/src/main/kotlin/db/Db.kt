package db

import android.content.Context
import co.appreactor.feedk.AtomLinkRel
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.time.OffsetDateTime

private const val FILE_NAME = "news-v5.db"

fun db(context: Context): Db {
    val driver = AndroidSqliteDriver(
        schema = Db.Schema,
        context = context,
        name = FILE_NAME,
        factory = RequerySQLiteOpenHelperFactory(),
    )

    return db(driver)
}

fun db(driver: SqlDriver): Db {
    return Db(
        driver = driver,
        EntryAdapter = entryAdapter(),
        FeedAdapter = feedAdapter(),
    )
}

fun Context.databaseFile(): File {
    return getDatabasePath(FILE_NAME)
}

private fun entryAdapter() = Entry.Adapter(
    publishedAdapter = offsetDateTimeAdapter(),
    updatedAdapter = offsetDateTimeAdapter(),
    linksAdapter = linksAdapter(),
)

private fun feedAdapter() = Feed.Adapter(
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

    private val gson = GsonBuilder()
        .registerTypeAdapter(HttpUrl::class.java, httpUrlAdapter())
        .registerTypeAdapter(AtomLinkRel::class.java, atomLinkRelAdapter())
        .create()

    override fun decode(databaseValue: String): List<Link> {
        return if (databaseValue.isEmpty()) {
            emptyList()
        } else {
            gson.fromJson(databaseValue, Array<Link>::class.java).asList()
        }
    }

    override fun encode(value: List<Link>) = gson.toJson(value)
}

private fun httpUrlAdapter(): TypeAdapter<HttpUrl> {
    return object : TypeAdapter<HttpUrl>() {
        override fun write(out: JsonWriter, value: HttpUrl?) {
            out.value(value.toString())
        }

        override fun read(`in`: JsonReader): HttpUrl {
            return `in`.nextString().toHttpUrl()
        }
    }
}

private fun atomLinkRelAdapter(): TypeAdapter<AtomLinkRel> {
    return object : TypeAdapter<AtomLinkRel>() {
        override fun write(out: JsonWriter, value: AtomLinkRel) {
            val string = when (value) {
                AtomLinkRel.Alternate -> "alternate"
                AtomLinkRel.Related -> "related"
                AtomLinkRel.Self -> "self"
                AtomLinkRel.Enclosure -> "enclosure"
                AtomLinkRel.Via -> "via"
                is AtomLinkRel.Custom -> value.value
            }

            out.value(string)
        }

        override fun read(`in`: JsonReader): AtomLinkRel {
            return when (val str = `in`.nextString()) {
                "alternate" -> AtomLinkRel.Alternate
                "related" -> AtomLinkRel.Related
                "self" -> AtomLinkRel.Self
                "enclosure" -> AtomLinkRel.Enclosure
                "via" -> AtomLinkRel.Via
                else -> AtomLinkRel.Custom(str)
            }
        }
    }
}