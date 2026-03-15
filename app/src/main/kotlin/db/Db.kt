package db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.time.OffsetDateTime

private const val FILE_NAME = "news-v6.db"

fun Context.databaseFile(): File {
    return getDatabasePath(FILE_NAME)
}

fun Context.db(): Db {
    val helper = DatabaseHelper(this)
    return Db.getInstance(helper.writableDatabase)
}

private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, FILE_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS feed (
                id TEXT PRIMARY KEY NOT NULL,
                links TEXT,
                title TEXT NOT NULL,
                ext_open_entries_in_browser INTEGER,
                ext_blocked_words TEXT NOT NULL,
                ext_show_preview_images INTEGER
            )
        """.trimIndent())
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS entry (
                content_type TEXT,
                content_src TEXT,
                content_text TEXT,
                links TEXT,
                summary TEXT,
                id TEXT PRIMARY KEY NOT NULL,
                feed_id TEXT NOT NULL,
                title TEXT NOT NULL,
                published TEXT NOT NULL,
                updated TEXT NOT NULL,
                author_name TEXT NOT NULL,
                ext_read INTEGER NOT NULL,
                ext_read_synced INTEGER NOT NULL,
                ext_bookmarked INTEGER NOT NULL,
                ext_bookmarked_synced INTEGER NOT NULL,
                ext_nc_guid_hash TEXT NOT NULL,
                ext_comments_url TEXT NOT NULL,
                ext_og_image_checked INTEGER NOT NULL,
                ext_og_image_url TEXT NOT NULL,
                ext_og_image_width INTEGER NOT NULL,
                ext_og_image_height INTEGER NOT NULL
            )
        """.trimIndent())
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conf (
                backend TEXT NOT NULL,
                miniflux_server_url TEXT NOT NULL,
                miniflux_server_trust_self_signed_certs INTEGER NOT NULL,
                miniflux_server_username TEXT NOT NULL,
                miniflux_server_password TEXT NOT NULL,
                nextcloud_server_url TEXT NOT NULL,
                nextcloud_server_trust_self_signed_certs INTEGER NOT NULL,
                nextcloud_server_username TEXT NOT NULL,
                nextcloud_server_password TEXT NOT NULL,
                initial_sync_completed INTEGER NOT NULL,
                last_entries_sync_datetime TEXT NOT NULL,
                show_read_entries INTEGER NOT NULL,
                sort_order TEXT NOT NULL,
                show_preview_images INTEGER NOT NULL,
                crop_preview_images INTEGER NOT NULL,
                mark_scrolled_entries_as_read INTEGER NOT NULL,
                sync_on_startup INTEGER NOT NULL,
                sync_in_background INTEGER NOT NULL,
                background_sync_interval_millis INTEGER NOT NULL,
                use_built_in_browser INTEGER NOT NULL,
                show_preview_text INTEGER NOT NULL,
                synced_on_startup INTEGER NOT NULL
            )
        """.trimIndent())
        
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entry_feed_id ON entry(feed_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entry_published ON entry(published)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migrations if needed
    }
}

class Db(private val database: SQLiteDatabase) {

    val entryQueries = EntryQueries(database)
    val feedQueries = FeedQueries(database)
    val entrySearchQueries = EntrySearchQueries(database)

    fun getDatabase(): SQLiteDatabase = database

    fun transaction(block: () -> Unit) {
        database.beginTransaction()
        try {
            block()
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun getInstance(database: SQLiteDatabase): Db {
            return instance ?: synchronized(this) {
                instance ?: Db(database).also { instance = it }
            }
        }
    }
}

class EntryQueries(private val database: SQLiteDatabase) {

    fun insertOrReplace(entry: Entry) {
        val values = ContentValues().apply {
            put("content_type", entry.contentType)
            put("content_src", entry.contentSrc)
            put("content_text", entry.contentText)
            put("links", linksToJson(entry.links))
            put("summary", entry.summary)
            put("id", entry.id)
            put("feed_id", entry.feedId)
            put("title", entry.title)
            put("published", entry.published.toString())
            put("updated", entry.updated.toString())
            put("author_name", entry.authorName)
            put("ext_read", if (entry.extRead) 1 else 0)
            put("ext_read_synced", if (entry.extReadSynced) 1 else 0)
            put("ext_bookmarked", if (entry.extBookmarked) 1 else 0)
            put("ext_bookmarked_synced", if (entry.extBookmarkedSynced) 1 else 0)
            put("ext_nc_guid_hash", entry.extNextcloudGuidHash)
            put("ext_comments_url", entry.extCommentsUrl)
            put("ext_og_image_checked", if (entry.extOpenGraphImageChecked) 1 else 0)
            put("ext_og_image_url", entry.extOpenGraphImageUrl)
            put("ext_og_image_width", entry.extOpenGraphImageWidth)
            put("ext_og_image_height", entry.extOpenGraphImageHeight)
        }
        database.insertWithOnConflict("entry", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun selectAllLinksPublishedAndTitle(): List<ShortEntry> {
        val res = mutableListOf<ShortEntry>()
        val cursor = database.rawQuery(
            "SELECT links, published, title FROM entry ORDER BY published DESC",
            emptyArray()
        )
        while (cursor.moveToNext()) {
            res.add(
                ShortEntry(
                    links = jsonToLinks(cursor.getString(0)),
                    published = OffsetDateTime.parse(cursor.getString(1)),
                    title = cursor.getString(2)
                )
            )
        }
        cursor.close()
        return res
    }

    fun selectById(entryId: String): Entry? {
        val cursor = database.rawQuery(
            "SELECT * FROM entry WHERE id = ?",
            arrayOf(entryId)
        )
        val entry = if (cursor.moveToNext()) cursorToEntry(cursor) else null
        cursor.close()
        return entry
    }

    fun selectByIds(ids: List<String>): List<Entry> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val query = "SELECT * FROM entry WHERE id IN ($placeholders)"
        val res = mutableListOf<Entry>()
        val cursor = database.rawQuery(query, ids.toTypedArray())
        while (cursor.moveToNext()) {
            res.add(cursorToEntry(cursor))
        }
        cursor.close()
        return res
    }

    fun selectByFeedIdAndReadAndBookmarked(
        feedId: String,
        extRead: List<Boolean>,
        extBookmarked: Boolean
    ): List<EntriesAdapterRow> {
        val readValues = extRead.map { if (it) 1 else 0 }
        val placeholders = readValues.joinToString(",") { "?" }
        val query = """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.feed_id = ? AND e.ext_read IN ($placeholders) AND e.ext_bookmarked = ?
            ORDER BY e.published DESC
        """.trimIndent()
        
        val readStrings = readValues.map { it.toString() }
        val bookmarkedString = if (extBookmarked) "1" else "0"
        val args = arrayOf(feedId, *readStrings.toTypedArray(), bookmarkedString)
        val res = mutableListOf<EntriesAdapterRow>()
        val cursor = database.rawQuery(query, args)
        while (cursor.moveToNext()) {
            res.add(cursorToEntriesAdapterRow(cursor))
        }
        cursor.close()
        return res
    }

    fun selectByReadAndBookmarked(
        extRead: List<Boolean>,
        extBookmarked: Boolean
    ): List<EntriesAdapterRow> {
        val readValues = extRead.map { if (it) 1 else 0 }
        val placeholders = readValues.joinToString(",") { "?" }
        val query = """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read IN ($placeholders) AND e.ext_bookmarked = ?
            ORDER BY e.published DESC
            LIMIT 500
        """.trimIndent()
        
        val readStrings = readValues.map { it.toString() }
        val bookmarkedString = if (extBookmarked) "1" else "0"
        val args = arrayOf(*readStrings.toTypedArray(), bookmarkedString)
        val res = mutableListOf<EntriesAdapterRow>()
        val cursor = database.rawQuery(query, args)
        while (cursor.moveToNext()) {
            res.add(cursorToEntriesAdapterRow(cursor))
        }
        cursor.close()
        return res
    }

    fun selectCount(): Long {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM entry", emptyArray())
        val count = if (cursor.moveToNext()) cursor.getLong(0) else 0L
        cursor.close()
        return count
    }

    fun selectMaxId(): String? {
        val cursor = database.rawQuery("SELECT MAX(CAST(id AS INTEGER)) FROM entry", emptyArray())
        val maxId = if (cursor.moveToNext()) cursor.getString(0) else null
        cursor.close()
        return maxId
    }

    fun selectMaxUpdated(): String? {
        val cursor = database.rawQuery("SELECT MAX(updated) FROM entry", emptyArray())
        val maxUpdated = if (cursor.moveToNext()) cursor.getString(0) else null
        cursor.close()
        return maxUpdated
    }

    fun selectByFtsQuery(query: String): List<SelectByQuery> {
        val searchQuery = "%$query%"
        val sql = """
            SELECT e.id, f.ext_show_preview_images, e.ext_og_image_url, e.ext_og_image_width, 
                   e.ext_og_image_height, e.title, f.title as feed_title, e.published, 
                   e.summary, e.ext_read, f.ext_open_entries_in_browser, e.links
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.title LIKE ? OR e.summary LIKE ? OR e.content_text LIKE ?
            LIMIT 500
        """.trimIndent()
        
        val res = mutableListOf<SelectByQuery>()
        val cursor = database.rawQuery(sql, arrayOf(searchQuery, searchQuery, searchQuery))
        while (cursor.moveToNext()) {
            res.add(cursorToSelectByQuery(cursor))
        }
        cursor.close()
        return res
    }

    fun updateReadByFeedId(read: Boolean, feedId: String) {
        val values = ContentValues().apply {
            put("ext_read", if (read) 1 else 0)
            put("ext_read_synced", 0)
        }
        val readInt = if (read) 0 else 1
        database.update("entry", values, "ext_read != ? AND feed_id = ?", arrayOf(readInt.toString(), feedId))
    }

    fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        val values = ContentValues().apply {
            put("ext_read", if (read) 1 else 0)
            put("ext_read_synced", 0)
        }
        val readInt = if (read) 0 else 1
        val bookmarkedInt = if (bookmarked) 1 else 0
        database.update("entry", values, "ext_read != ? AND ext_bookmarked = ?", arrayOf(readInt.toString(), bookmarkedInt.toString()))
    }

    fun updateReadAndReadSynced(id: String, extRead: Boolean, extReadSynced: Boolean) {
        val values = ContentValues().apply {
            put("ext_read", if (extRead) 1 else 0)
            put("ext_read_synced", if (extReadSynced) 1 else 0)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun updateBookmarkedAndBookmaredSynced(id: String, extBookmarked: Boolean, extBookmarkedSynced: Boolean) {
        val values = ContentValues().apply {
            put("ext_bookmarked", if (extBookmarked) 1 else 0)
            put("ext_bookmarked_synced", if (extBookmarkedSynced) 1 else 0)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun selectByReadSynced(extReadSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val extReadSyncedInt = if (extReadSynced) "1" else "0"
        val cursor = database.rawQuery(
            "SELECT links, summary, id, feed_id, title, published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced, ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url, ext_og_image_width, ext_og_image_height FROM entry WHERE ext_read_synced = ? ORDER BY published DESC",
            arrayOf(extReadSyncedInt)
        )
        while (cursor.moveToNext()) {
            res.add(cursorToEntryWithoutContent(cursor))
        }
        cursor.close()
        return res
    }

    fun selectByBookmarkedSynced(extBookmarkedSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val extBookmarkedSyncedInt = if (extBookmarkedSynced) "1" else "0"
        val cursor = database.rawQuery(
            "SELECT links, summary, id, feed_id, title, published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced, ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url, ext_og_image_width, ext_og_image_height FROM entry WHERE ext_bookmarked_synced = ? ORDER BY published DESC",
            arrayOf(extBookmarkedSyncedInt)
        )
        while (cursor.moveToNext()) {
            res.add(cursorToEntryWithoutContent(cursor))
        }
        cursor.close()
        return res
    }

    fun selectLinksById(id: String): List<Link>? {
        val cursor = database.rawQuery("SELECT links FROM entry WHERE id = ?", arrayOf(id))
        val links = if (cursor.moveToNext()) jsonToLinks(cursor.getString(0)) else null
        cursor.close()
        return links
    }

    fun selectAllLinks(): List<List<Link>> {
        val res = mutableListOf<List<Link>>()
        val cursor = database.rawQuery("SELECT links FROM entry", emptyArray())
        while (cursor.moveToNext()) {
            res.add(jsonToLinks(cursor.getString(0)))
        }
        cursor.close()
        return res
    }

    fun updateLinks(id: String, links: List<Link>) {
        val values = ContentValues().apply {
            put("links", linksToJson(links))
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun updateOgImageChecked(extOgImageChecked: Boolean, id: String) {
        val values = ContentValues().apply {
            put("ext_og_image_checked", if (extOgImageChecked) 1 else 0)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun updateOgImage(extOgImageUrl: String, extOgImageWidth: Long, extOgImageHeight: Long, id: String) {
        val values = ContentValues().apply {
            put("ext_og_image_url", extOgImageUrl)
            put("ext_og_image_width", extOgImageWidth)
            put("ext_og_image_height", extOgImageHeight)
            put("ext_og_image_checked", 1)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun updateReadSynced(extReadSynced: Boolean, id: String) {
        val values = ContentValues().apply {
            put("ext_read_synced", if (extReadSynced) 1 else 0)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun updateBookmarkedSynced(extBookmarkedSynced: Boolean, id: String) {
        val values = ContentValues().apply {
            put("ext_bookmarked_synced", if (extBookmarkedSynced) 1 else 0)
        }
        database.update("entry", values, "id = ?", arrayOf(id))
    }

    fun deleteByFeedId(feedId: String) {
        database.delete("entry", "feed_id = ?", arrayOf(feedId))
    }

    fun deleteAll() {
        database.delete("entry", null, null)
    }

    fun selectByOgImageChecked(extOgImageChecked: Boolean, limit: Long): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val extOgImageCheckedInt = if (extOgImageChecked) "1" else "0"
        val cursor = database.rawQuery(
            "SELECT links, summary, id, feed_id, title, published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced, ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url, ext_og_image_width, ext_og_image_height FROM entry WHERE ext_og_image_checked = ? ORDER BY published DESC LIMIT ?",
            arrayOf(extOgImageCheckedInt, limit.toString())
        )
        while (cursor.moveToNext()) {
            res.add(cursorToEntryWithoutContent(cursor))
        }
        cursor.close()
        return res
    }

    private fun cursorToEntry(cursor: Cursor): Entry {
        return Entry(
            contentType = cursor.getString(0),
            contentSrc = cursor.getString(1),
            contentText = cursor.getString(2),
            links = jsonToLinks(cursor.getString(3)),
            summary = cursor.getString(4),
            id = cursor.getString(5),
            feedId = cursor.getString(6),
            title = cursor.getString(7),
            published = OffsetDateTime.parse(cursor.getString(8)),
            updated = OffsetDateTime.parse(cursor.getString(9)),
            authorName = cursor.getString(10),
            extRead = cursor.getInt(11) == 1,
            extReadSynced = cursor.getInt(12) == 1,
            extBookmarked = cursor.getInt(13) == 1,
            extBookmarkedSynced = cursor.getInt(14) == 1,
            extNextcloudGuidHash = cursor.getString(15),
            extCommentsUrl = cursor.getString(16),
            extOpenGraphImageChecked = cursor.getInt(17) == 1,
            extOpenGraphImageUrl = cursor.getString(18),
            extOpenGraphImageWidth = cursor.getInt(19),
            extOpenGraphImageHeight = cursor.getInt(20)
        )
    }

    private fun cursorToEntriesAdapterRow(cursor: Cursor): EntriesAdapterRow {
        return EntriesAdapterRow(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            feedId = cursor.getString(cursor.getColumnIndexOrThrow("feed_id")),
            extBookmarked = cursor.getInt(cursor.getColumnIndexOrThrow("ext_bookmarked")) == 1,
            extShowPreviewImages = cursor.getInt(cursor.getColumnIndexOrThrow("ext_show_preview_images")) == 1,
            extOpenGraphImageUrl = cursor.getString(cursor.getColumnIndexOrThrow("ext_og_image_url")),
            extOpenGraphImageWidth = cursor.getInt(cursor.getColumnIndexOrThrow("ext_og_image_width")),
            extOpenGraphImageHeight = cursor.getInt(cursor.getColumnIndexOrThrow("ext_og_image_height")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            feedTitle = cursor.getString(cursor.getColumnIndexOrThrow("feed_title")),
            published = OffsetDateTime.parse(cursor.getString(cursor.getColumnIndexOrThrow("published"))),
            summary = cursor.getString(cursor.getColumnIndexOrThrow("summary")) ?: "",
            extRead = cursor.getInt(cursor.getColumnIndexOrThrow("ext_read")) == 1,
            extOpenEntriesInBrowser = cursor.getInt(cursor.getColumnIndexOrThrow("ext_open_entries_in_browser")) == 1,
            links = jsonToLinks(cursor.getString(cursor.getColumnIndexOrThrow("links")))
        )
    }

    private fun cursorToSelectByQuery(cursor: Cursor): SelectByQuery {
        return SelectByQuery(
            id = cursor.getString(0),
            extShowPreviewImages = cursor.getInt(1) == 1,
            extOpenGraphImageUrl = cursor.getString(2),
            extOpenGraphImageWidth = cursor.getInt(3),
            extOpenGraphImageHeight = cursor.getInt(4),
            title = cursor.getString(5),
            feedTitle = cursor.getString(6),
            published = OffsetDateTime.parse(cursor.getString(7)),
            summary = cursor.getString(8),
            extRead = cursor.getInt(9) == 1,
            extOpenEntriesInBrowser = cursor.getInt(10) == 1,
            links = jsonToLinks(cursor.getString(11))
        )
    }

    private fun cursorToEntryWithoutContent(cursor: Cursor): EntryWithoutContent {
        return EntryWithoutContent(
            links = jsonToLinks(cursor.getString(0)),
            summary = cursor.getString(1),
            id = cursor.getString(2),
            feedId = cursor.getString(3),
            title = cursor.getString(4),
            published = OffsetDateTime.parse(cursor.getString(5)),
            updated = OffsetDateTime.parse(cursor.getString(6)),
            authorName = cursor.getString(7),
            extRead = cursor.getInt(8) == 1,
            extReadSynced = cursor.getInt(9) == 1,
            extBookmarked = cursor.getInt(10) == 1,
            extBookmarkedSynced = cursor.getInt(11) == 1,
            extNextcloudGuidHash = cursor.getString(12),
            extCommentsUrl = cursor.getString(13),
            extOpenGraphImageChecked = cursor.getInt(14) == 1,
            extOpenGraphImageUrl = cursor.getString(15),
            extOpenGraphImageWidth = cursor.getInt(16),
            extOpenGraphImageHeight = cursor.getInt(17)
        )
    }
}

class FeedQueries(private val database: SQLiteDatabase) {

    fun insertOrReplace(feed: Feed) {
        val values = ContentValues().apply {
            put("id", feed.id)
            put("links", linksToJson(feed.links))
            put("title", feed.title)
            put("ext_open_entries_in_browser", if (feed.extOpenEntriesInBrowser == true) 1 else 0)
            put("ext_blocked_words", feed.extBlockedWords)
            put("ext_show_preview_images", if (feed.extShowPreviewImages == true) 1 else 0)
        }
        database.insertWithOnConflict("feed", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun selectAll(): List<Feed> {
        val res = mutableListOf<Feed>()
        val cursor = database.rawQuery("SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed ORDER BY title", emptyArray())
        while (cursor.moveToNext()) {
            res.add(cursorToFeed(cursor))
        }
        cursor.close()
        return res
    }

    fun selectAllWithUnreadEntryCount(): List<SelectAllWithUnreadEntryCount> {
        val res = mutableListOf<SelectAllWithUnreadEntryCount>()
        val sql = """
            SELECT f.id, f.links, f.title, f.ext_open_entries_in_browser, f.ext_blocked_words, f.ext_show_preview_images, COUNT(e.id) as unread_entries
            FROM feed f
            LEFT JOIN entry e ON e.feed_id = f.id AND e.ext_read = 0 AND e.ext_bookmarked = 0
            GROUP BY f.id
            ORDER BY f.title
        """.trimIndent()
        
        val cursor = database.rawQuery(sql, emptyArray())
        while (cursor.moveToNext()) {
            res.add(
                SelectAllWithUnreadEntryCount(
                    id = cursor.getString(0),
                    links = jsonToLinks(cursor.getString(1)),
                    title = cursor.getString(2),
                    extOpenEntriesInBrowser = cursor.getInt(3) == 1,
                    extBlockedWords = cursor.getString(4),
                    extShowPreviewImages = cursor.getInt(5) == 1,
                    unreadEntries = cursor.getLong(6)
                )
            )
        }
        cursor.close()
        return res
    }

    fun selectById(id: String): Feed? {
        val cursor = database.rawQuery(
            "SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed WHERE id = ?",
            arrayOf(id)
        )
        val feed = if (cursor.moveToNext()) cursorToFeed(cursor) else null
        cursor.close()
        return feed
    }

    fun selectLinks(): List<List<Link>> {
        val res = mutableListOf<List<Link>>()
        val cursor = database.rawQuery("SELECT links FROM entry", emptyArray())
        while (cursor.moveToNext()) {
            res.add(jsonToLinks(cursor.getString(0)))
        }
        cursor.close()
        return res
    }

    fun deleteById(id: String) {
        database.delete("feed", "id = ?", arrayOf(id))
    }

    fun deleteAll() {
        database.delete("feed", null, null)
    }

    private fun cursorToFeed(cursor: Cursor): Feed {
        return Feed(
            id = cursor.getString(0),
            links = jsonToLinks(cursor.getString(1)),
            title = cursor.getString(2),
            extOpenEntriesInBrowser = cursor.getInt(3) == 1,
            extBlockedWords = cursor.getString(4),
            extShowPreviewImages = cursor.getInt(5) == 1
        )
    }
}

data class SelectByQuery(
    val id: String,
    val extShowPreviewImages: Boolean,
    val extOpenGraphImageUrl: String,
    val extOpenGraphImageWidth: Int,
    val extOpenGraphImageHeight: Int,
    val title: String,
    val feedTitle: String,
    val published: OffsetDateTime,
    val summary: String?,
    val extRead: Boolean,
    val extOpenEntriesInBrowser: Boolean,
    val links: List<Link>
)

data class SelectAllWithUnreadEntryCount(
    val id: String,
    val links: List<Link>,
    val title: String,
    val extOpenEntriesInBrowser: Boolean,
    val extBlockedWords: String,
    val extShowPreviewImages: Boolean,
    val unreadEntries: Long
)

private fun linksToJson(links: List<Link>): String {
    return links.joinToString(",") { link ->
        """{"href":"${link.href}","rel":"${link.rel}","type":"${link.type}"}"""
    }
}

private fun jsonToLinks(json: String?): List<Link> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val links = mutableListOf<Link>()
        val regex = """\{"href":"([^"]*)","rel":"([^"]*)","type":"([^"]*)"\}""".toRegex()
        regex.findAll(json).forEach { match ->
            val (href, rel, type) = match.destructured
            val parsedRel: co.appreactor.feedk.AtomLinkRel = when (rel) {
                "Alternate" -> co.appreactor.feedk.AtomLinkRel.Alternate
                "Enclosure" -> co.appreactor.feedk.AtomLinkRel.Enclosure
                "Self" -> co.appreactor.feedk.AtomLinkRel.Self
                "Related" -> co.appreactor.feedk.AtomLinkRel.Related
                else -> co.appreactor.feedk.AtomLinkRel.Alternate
            }
            val parsedUrl = if (href.startsWith("http")) {
                href.toHttpUrlOrNull() ?: return@forEach
            } else {
                return@forEach
            }
            links.add(
                Link(
                    feedId = null,
                    entryId = null,
                    href = parsedUrl,
                    rel = parsedRel,
                    type = type,
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null
                )
            )
        }
        links
    } catch (e: Exception) {
        emptyList()
    }
}

class EntrySearchQueries(private val database: SQLiteDatabase) {
    fun selectByQuery(query: String): List<SelectByQuery> {
        val searchQuery = "%$query%"
        val sql = """
            SELECT e.id, f.ext_show_preview_images, e.ext_og_image_url, e.ext_og_image_width, 
                   e.ext_og_image_height, e.title, f.title as feed_title, e.published, 
                   e.summary, e.ext_read, f.ext_open_entries_in_browser, e.links
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.title LIKE ? OR e.summary LIKE ? OR e.content_text LIKE ?
            LIMIT 500
        """.trimIndent()
        
        val res = mutableListOf<SelectByQuery>()
        val cursor = database.rawQuery(sql, arrayOf(searchQuery, searchQuery, searchQuery))
        while (cursor.moveToNext()) {
            res.add(
                SelectByQuery(
                    id = cursor.getString(0),
                    extShowPreviewImages = cursor.getInt(1) == 1,
                    extOpenGraphImageUrl = cursor.getString(2),
                    extOpenGraphImageWidth = cursor.getInt(3),
                    extOpenGraphImageHeight = cursor.getInt(4),
                    title = cursor.getString(5),
                    feedTitle = cursor.getString(6),
                    published = OffsetDateTime.parse(cursor.getString(7)),
                    summary = cursor.getString(8),
                    extRead = cursor.getInt(9) == 1,
                    extOpenEntriesInBrowser = cursor.getInt(10) == 1,
                    links = jsonToLinks(cursor.getString(11))
                )
            )
        }
        cursor.close()
        return res
    }
}
