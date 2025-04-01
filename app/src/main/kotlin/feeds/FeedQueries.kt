package feeds

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import db.Feed
import db.Link
import db.getNullableBoolean

class FeedQueries(private val database: SQLiteDatabase) {
    companion object {
        private val PROJECTION_FULL = arrayOf(
            "id",
            "links",
            "title",
            "ext_open_entries_in_browser",
            "ext_blocked_words",
            "ext_show_preview_images",
        )
    }

    fun selectAll(): List<Feed> {
        val res = mutableListOf<Feed>()
        val cursor = database.query("feed", PROJECTION_FULL, "", emptyArray(), "", "", "", "")
        while (cursor.moveToNext()) {
            res += Feed(
                id = cursor.getString(0),
                links = cursor.getLinks(1),
                title = cursor.getString(2),
                extOpenEntriesInBrowser = cursor.getNullableBoolean(3),
                extBlockedWords = cursor.getString(4),
                extShowPreviewImages = cursor.getNullableBoolean(5),
            )
        }
        return res
    }

    private fun Cursor.getLinks(columnIndex: Int): List<Link> {
        return emptyList() // TODO
    }
}