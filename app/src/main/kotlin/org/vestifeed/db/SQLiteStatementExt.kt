package org.vestifeed.db

import androidx.sqlite.SQLiteStatement
import com.google.gson.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZonedDateTime

fun SQLiteStatement.bindTextOrNull(index: Int, value: String?) {
    if (value == null) bindNull(index) else bindText(index, value)
}

fun SQLiteStatement.bindBooleanOrNull(index: Int, value: Boolean?) {
    if (value == null) bindNull(index) else bindInt(index, if (value) 1 else 0)
}

fun SQLiteStatement.bindLongOrNull(index: Int, value: Long?) {
    if (value == null) bindNull(index) else bindLong(index, value)
}

fun SQLiteStatement.bindHttpUrlOrNull(index: Int, value: HttpUrl?) {
    if (value == null) bindNull(index) else bindText(index, value.toString())
}

fun SQLiteStatement.bindZonedDateTimeOrNull(index: Int, value: ZonedDateTime?) {
    if (value == null) bindNull(index) else bindText(index, value.toString())
}

fun SQLiteStatement.bindJsonObjectOrNull(index: Int, value: JsonObject?) {
    if (value == null) bindNull(index) else bindText(index, value.toString())
}

fun SQLiteStatement.getTextOrNull(index: Int): String? =
    if (isNull(index)) null else getText(index)

fun SQLiteStatement.getBoolOrNull(index: Int): Boolean? =
    if (isNull(index)) null else getInt(index) != 0

fun SQLiteStatement.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

fun SQLiteStatement.getZonedDateTimeOrNull(index: Int): ZonedDateTime? =
    if (isNull(index)) null else ZonedDateTime.parse(getText(index))

fun SQLiteStatement.getHttpUrlOrNull(index: Int): HttpUrl? =
    if (isNull(index)) null else getText(index).toHttpUrl()

fun SQLiteStatement.getJsonObjectOrNull(index: Int): JsonObject? =
    if (isNull(index)) null else com.google.gson.JsonParser.parseString(getText(index)).asJsonObject