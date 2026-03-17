package db

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.time.OffsetDateTime

private const val FILE_NAME = "vesti-2026-03-17.db"

internal fun loadSchema(): String {
    return Db::class.java.classLoader.getResourceAsStream("schema.sql")?.bufferedReader()?.use { it.readText() }
        ?: error("schema.sql not found in resources")
}

internal fun executeSchema(conn: SQLiteConnection) {
    val schema = loadSchema()
    schema.split(";").forEach { statement ->
        val trimmed = statement.trim()
        if (trimmed.isNotEmpty()) {
            conn.execSQL(trimmed)
        }
    }
}

fun Context.databaseFile(): File {
    return getDatabasePath(FILE_NAME)
}

fun Context.db(): Db {
    val path = getDatabasePath(FILE_NAME).absolutePath
    return Db(AndroidSQLiteDriver().open(path))
}

class Db(val conn: SQLiteConnection, initSchema: Boolean) {

    constructor(conn: SQLiteConnection) : this(conn, true)

    val entryQueries = EntryQueries(conn)
    val feedQueries = FeedQueries(conn)
    val entrySearchQueries = EntrySearchQueries(conn)

    init {
        if (initSchema) {
            executeSchema(conn)
        }
    }

    fun getConnection(): SQLiteConnection = conn

    fun transaction(block: () -> Unit) {
        conn.execSQL("BEGIN TRANSACTION")
        try {
            block()
            conn.execSQL("COMMIT")
        } catch (e: Exception) {
            conn.execSQL("ROLLBACK")
            throw e
        }
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun getInstance(connection: SQLiteConnection): Db {
            return instance ?: synchronized(this) {
                instance ?: Db(connection).also { instance = it }
            }
        }
    }
}

class EntryQueries(private val conn: SQLiteConnection) {

    fun insertOrReplace(entry: Entry) {
        val stmt = conn.prepare("""
            INSERT OR REPLACE INTO entry (
                content_type, content_src, content_text, links, summary, id, feed_id, title,
                published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked,
                ext_bookmarked_synced, ext_nc_guid_hash, ext_comments_url, ext_og_image_checked,
                ext_og_image_url, ext_og_image_width, ext_og_image_height
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """)
        if (entry.contentType != null) stmt.bindText(1, entry.contentType) else stmt.bindNull(1)
        if (entry.contentSrc != null) stmt.bindText(2, entry.contentSrc) else stmt.bindNull(2)
        if (entry.contentText != null) stmt.bindText(3, entry.contentText) else stmt.bindNull(3)
        stmt.bindText(4, linksToJson(entry.links))
        if (entry.summary != null) stmt.bindText(5, entry.summary) else stmt.bindNull(5)
        stmt.bindText(6, entry.id)
        stmt.bindText(7, entry.feedId)
        stmt.bindText(8, entry.title)
        stmt.bindText(9, entry.published.toString())
        stmt.bindText(10, entry.updated.toString())
        stmt.bindText(11, entry.authorName)
        stmt.bindInt(12, if (entry.extRead) 1 else 0)
        stmt.bindInt(13, if (entry.extReadSynced) 1 else 0)
        stmt.bindInt(14, if (entry.extBookmarked) 1 else 0)
        stmt.bindInt(15, if (entry.extBookmarkedSynced) 1 else 0)
        stmt.bindText(16, entry.extNextcloudGuidHash)
        stmt.bindText(17, entry.extCommentsUrl)
        stmt.bindInt(18, if (entry.extOpenGraphImageChecked) 1 else 0)
        stmt.bindText(19, entry.extOpenGraphImageUrl)
        stmt.bindInt(20, entry.extOpenGraphImageWidth)
        stmt.bindInt(21, entry.extOpenGraphImageHeight)
        stmt.step()
        stmt.close()
    }

    fun selectAllLinksPublishedAndTitle(): List<ShortEntry> {
        val res = mutableListOf<ShortEntry>()
        val stmt = conn.prepare("SELECT links, published, title FROM entry ORDER BY published DESC")
        while (stmt.step()) {
            res.add(
                ShortEntry(
                    links = jsonToLinks(stmt.getText(0)),
                    published = OffsetDateTime.parse(stmt.getText(1)),
                    title = stmt.getText(2)
                )
            )
        }
        stmt.close()
        return res
    }

    fun selectById(entryId: String): Entry? {
        val stmt = conn.prepare("SELECT * FROM entry WHERE id = ?")
        stmt.bindText(1, entryId)
        val entry = if (stmt.step()) statementToEntry(stmt) else null
        stmt.close()
        return entry
    }

    fun selectByIds(ids: List<String>): List<Entry> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val query = "SELECT * FROM entry WHERE id IN ($placeholders)"
        val res = mutableListOf<Entry>()
        val stmt = conn.prepare(query)
        ids.forEachIndexed { index, id ->
            stmt.bindText(index + 1, id)
        }
        while (stmt.step()) {
            res.add(statementToEntry(stmt))
        }
        stmt.close()
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

        val res = mutableListOf<EntriesAdapterRow>()
        val stmt = conn.prepare(query)
        stmt.bindText(1, feedId)
        readValues.forEachIndexed { index, value ->
            stmt.bindInt(index + 2, value)
        }
        stmt.bindInt(2 + readValues.size, if (extBookmarked) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntriesAdapterRow(stmt))
        }
        stmt.close()
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

        val res = mutableListOf<EntriesAdapterRow>()
        val stmt = conn.prepare(query)
        readValues.forEachIndexed { index, value ->
            stmt.bindInt(index + 1, value)
        }
        stmt.bindInt(1 + readValues.size, if (extBookmarked) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntriesAdapterRow(stmt))
        }
        stmt.close()
        return res
    }

    fun selectCount(): Long {
        val stmt = conn.prepare("SELECT COUNT(*) FROM entry")
        val count = if (stmt.step()) stmt.getLong(0) else 0L
        stmt.close()
        return count
    }

    fun selectMaxId(): String? {
        val stmt = conn.prepare("SELECT MAX(CAST(id AS INTEGER)) FROM entry")
        val maxId = if (stmt.step()) stmt.getText(0) else null
        stmt.close()
        return maxId
    }

    fun selectMaxUpdated(): String? {
        val stmt = conn.prepare("SELECT MAX(updated) FROM entry")
        val maxUpdated = if (stmt.step()) stmt.getText(0) else null
        stmt.close()
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
        val stmt = conn.prepare(sql)
        stmt.bindText(1, searchQuery)
        stmt.bindText(2, searchQuery)
        stmt.bindText(3, searchQuery)
        while (stmt.step()) {
            res.add(statementToSelectByQuery(stmt))
        }
        stmt.close()
        return res
    }

    fun updateReadByFeedId(read: Boolean, feedId: String) {
        val readInt = if (read) 0 else 1
        val stmt = conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = 0 WHERE ext_read != ? AND feed_id = ?")
        stmt.bindInt(1, if (read) 1 else 0)
        stmt.bindInt(2, readInt)
        stmt.bindText(3, feedId)
        stmt.step()
        stmt.close()
    }

    fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        val readInt = if (read) 0 else 1
        val bookmarkedInt = if (bookmarked) 1 else 0
        val stmt = conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = 0 WHERE ext_read != ? AND ext_bookmarked = ?")
        stmt.bindInt(1, if (read) 1 else 0)
        stmt.bindInt(2, readInt)
        stmt.bindInt(3, bookmarkedInt)
        stmt.step()
        stmt.close()
    }

    fun updateReadAndReadSynced(id: String, extRead: Boolean, extReadSynced: Boolean) {
        val stmt = conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = ? WHERE id = ?")
        stmt.bindInt(1, if (extRead) 1 else 0)
        stmt.bindInt(2, if (extReadSynced) 1 else 0)
        stmt.bindText(3, id)
        stmt.step()
        stmt.close()
    }

    fun updateBookmarkedAndBookmaredSynced(id: String, extBookmarked: Boolean, extBookmarkedSynced: Boolean) {
        val stmt = conn.prepare("UPDATE entry SET ext_bookmarked = ?, ext_bookmarked_synced = ? WHERE id = ?")
        stmt.bindInt(1, if (extBookmarked) 1 else 0)
        stmt.bindInt(2, if (extBookmarkedSynced) 1 else 0)
        stmt.bindText(3, id)
        stmt.step()
        stmt.close()
    }

    fun selectByReadSynced(extReadSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val stmt = conn.prepare("""
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_read_synced = ? ORDER BY published DESC
        """)
        stmt.bindInt(1, if (extReadSynced) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntryWithoutContent(stmt))
        }
        stmt.close()
        return res
    }

    fun selectByBookmarkedSynced(extBookmarkedSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val stmt = conn.prepare("""
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_bookmarked_synced = ? ORDER BY published DESC
        """)
        stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntryWithoutContent(stmt))
        }
        stmt.close()
        return res
    }

    fun selectLinksById(id: String): List<Link>? {
        val stmt = conn.prepare("SELECT links FROM entry WHERE id = ?")
        stmt.bindText(1, id)
        val links = if (stmt.step()) jsonToLinks(stmt.getText(0)) else null
        stmt.close()
        return links
    }

    fun selectAllLinks(): List<List<Link>> {
        val res = mutableListOf<List<Link>>()
        val stmt = conn.prepare("SELECT links FROM entry")
        while (stmt.step()) {
            res.add(jsonToLinks(stmt.getText(0)))
        }
        stmt.close()
        return res
    }

    fun updateLinks(id: String, links: List<Link>) {
        val stmt = conn.prepare("UPDATE entry SET links = ? WHERE id = ?")
        stmt.bindText(1, linksToJson(links))
        stmt.bindText(2, id)
        stmt.step()
        stmt.close()
    }

    fun updateOgImageChecked(extOgImageChecked: Boolean, id: String) {
        val stmt = conn.prepare("UPDATE entry SET ext_og_image_checked = ? WHERE id = ?")
        stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
        stmt.bindText(2, id)
        stmt.step()
        stmt.close()
    }

    fun updateOgImage(extOgImageUrl: String, extOgImageWidth: Long, extOgImageHeight: Long, id: String) {
        val stmt = conn.prepare("UPDATE entry SET ext_og_image_url = ?, ext_og_image_width = ?, ext_og_image_height = ?, ext_og_image_checked = 1 WHERE id = ?")
        stmt.bindText(1, extOgImageUrl)
        stmt.bindLong(2, extOgImageWidth)
        stmt.bindLong(3, extOgImageHeight)
        stmt.bindText(4, id)
        stmt.step()
        stmt.close()
    }

    fun updateReadSynced(extReadSynced: Boolean, id: String) {
        val stmt = conn.prepare("UPDATE entry SET ext_read_synced = ? WHERE id = ?")
        stmt.bindInt(1, if (extReadSynced) 1 else 0)
        stmt.bindText(2, id)
        stmt.step()
        stmt.close()
    }

    fun updateBookmarkedSynced(extBookmarkedSynced: Boolean, id: String) {
        val stmt = conn.prepare("UPDATE entry SET ext_bookmarked_synced = ? WHERE id = ?")
        stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
        stmt.bindText(2, id)
        stmt.step()
        stmt.close()
    }

    fun deleteByFeedId(feedId: String) {
        val stmt = conn.prepare("DELETE FROM entry WHERE feed_id = ?")
        stmt.bindText(1, feedId)
        stmt.step()
        stmt.close()
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM entry")
    }

    fun selectByOgImageChecked(extOgImageChecked: Boolean, limit: Long): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val stmt = conn.prepare("""
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_og_image_checked = ? ORDER BY published DESC LIMIT ?
        """)
        stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
        stmt.bindLong(2, limit)
        while (stmt.step()) {
            res.add(statementToEntryWithoutContent(stmt))
        }
        stmt.close()
        return res
    }

    private fun statementToEntry(stmt: SQLiteStatement): Entry {
        return Entry(
            contentType = stmt.getText(0),
            contentSrc = stmt.getText(1),
            contentText = stmt.getText(2),
            links = jsonToLinks(stmt.getText(3)),
            summary = stmt.getText(4),
            id = stmt.getText(5),
            feedId = stmt.getText(6),
            title = stmt.getText(7),
            published = OffsetDateTime.parse(stmt.getText(8)),
            updated = OffsetDateTime.parse(stmt.getText(9)),
            authorName = stmt.getText(10),
            extRead = stmt.getInt(11) == 1,
            extReadSynced = stmt.getInt(12) == 1,
            extBookmarked = stmt.getInt(13) == 1,
            extBookmarkedSynced = stmt.getInt(14) == 1,
            extNextcloudGuidHash = stmt.getText(15),
            extCommentsUrl = stmt.getText(16),
            extOpenGraphImageChecked = stmt.getInt(17) == 1,
            extOpenGraphImageUrl = stmt.getText(18),
            extOpenGraphImageWidth = stmt.getInt(19),
            extOpenGraphImageHeight = stmt.getInt(20)
        )
    }

    private fun getColumnIndex(stmt: SQLiteStatement, name: String): Int {
        return stmt.getColumnNames().indexOf(name)
    }

    private fun SQLiteStatement.getTextSafe(index: Int): String? {
        return try {
            if (index < 0) null else getText(index)
        } catch (e: NullPointerException) {
            null
        }
    }

    private fun statementToEntriesAdapterRow(stmt: SQLiteStatement): EntriesAdapterRow {
        return EntriesAdapterRow(
            id = stmt.getTextSafe(getColumnIndex(stmt, "id")) ?: "",
            feedId = stmt.getTextSafe(getColumnIndex(stmt, "feed_id")) ?: "",
            extBookmarked = stmt.getInt(getColumnIndex(stmt, "ext_bookmarked")) == 1,
            extShowPreviewImages = stmt.getInt(getColumnIndex(stmt, "ext_show_preview_images")) == 1,
            extOpenGraphImageUrl = stmt.getTextSafe(getColumnIndex(stmt, "ext_og_image_url")) ?: "",
            extOpenGraphImageWidth = stmt.getInt(getColumnIndex(stmt, "ext_og_image_width")),
            extOpenGraphImageHeight = stmt.getInt(getColumnIndex(stmt, "ext_og_image_height")),
            title = stmt.getTextSafe(getColumnIndex(stmt, "title")) ?: "",
            feedTitle = stmt.getTextSafe(getColumnIndex(stmt, "feed_title")) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextSafe(getColumnIndex(stmt, "published"))) }.getOrDefault(OffsetDateTime.now()),
            summary = stmt.getTextSafe(getColumnIndex(stmt, "summary")) ?: "",
            extRead = stmt.getInt(getColumnIndex(stmt, "ext_read")) == 1,
            extOpenEntriesInBrowser = stmt.getInt(getColumnIndex(stmt, "ext_open_entries_in_browser")) == 1,
            links = jsonToLinks(stmt.getTextSafe(getColumnIndex(stmt, "links")))
        )
    }

    private fun statementToSelectByQuery(stmt: SQLiteStatement): SelectByQuery {
        return SelectByQuery(
            id = stmt.getTextSafe(0) ?: "",
            extShowPreviewImages = stmt.getInt(1) == 1,
            extOpenGraphImageUrl = stmt.getTextSafe(2) ?: "",
            extOpenGraphImageWidth = stmt.getInt(3),
            extOpenGraphImageHeight = stmt.getInt(4),
            title = stmt.getTextSafe(5) ?: "",
            feedTitle = stmt.getTextSafe(6) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextSafe(7)) }.getOrDefault(OffsetDateTime.now()),
            summary = stmt.getTextSafe(8) ?: "",
            extRead = stmt.getInt(9) == 1,
            extOpenEntriesInBrowser = stmt.getInt(10) == 1,
            links = jsonToLinks(stmt.getTextSafe(11))
        )
    }

    private fun statementToEntryWithoutContent(stmt: SQLiteStatement): EntryWithoutContent {
        return EntryWithoutContent(
            links = jsonToLinks(stmt.getTextSafe(0)),
            summary = stmt.getTextSafe(1),
            id = stmt.getTextSafe(2) ?: "",
            feedId = stmt.getTextSafe(3) ?: "",
            title = stmt.getTextSafe(4) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextSafe(5)) }.getOrDefault(OffsetDateTime.now()),
            updated = runCatching { OffsetDateTime.parse(stmt.getTextSafe(6)) }.getOrDefault(OffsetDateTime.now()),
            authorName = stmt.getTextSafe(7) ?: "",
            extRead = stmt.getInt(8) == 1,
            extReadSynced = stmt.getInt(9) == 1,
            extBookmarked = stmt.getInt(10) == 1,
            extBookmarkedSynced = stmt.getInt(11) == 1,
            extNextcloudGuidHash = stmt.getTextSafe(12) ?: "",
            extCommentsUrl = stmt.getTextSafe(13) ?: "",
            extOpenGraphImageChecked = stmt.getInt(14) == 1,
            extOpenGraphImageUrl = stmt.getTextSafe(15) ?: "",
            extOpenGraphImageWidth = stmt.getInt(16),
            extOpenGraphImageHeight = stmt.getInt(17)
        )
    }
}

class FeedQueries(private val conn: SQLiteConnection) {

    fun insertOrReplace(feed: Feed) {
        val stmt = conn.prepare("""
            INSERT OR REPLACE INTO feed (
                id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images
            ) VALUES (?, ?, ?, ?, ?, ?)
        """)
        stmt.bindText(1, feed.id)
        stmt.bindText(2, linksToJson(feed.links))
        stmt.bindText(3, feed.title)
        stmt.bindInt(4, if (feed.extOpenEntriesInBrowser == true) 1 else 0)
        stmt.bindText(5, feed.extBlockedWords)
        stmt.bindInt(6, if (feed.extShowPreviewImages == true) 1 else 0)
        stmt.step()
        stmt.close()
    }

    fun selectAll(): List<Feed> {
        val res = mutableListOf<Feed>()
        val stmt = conn.prepare("SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed ORDER BY title")
        while (stmt.step()) {
            res.add(statementToFeed(stmt))
        }
        stmt.close()
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

        val stmt = conn.prepare(sql)
        while (stmt.step()) {
            res.add(
                SelectAllWithUnreadEntryCount(
                    id = stmt.getText(0),
                    links = jsonToLinks(stmt.getText(1)),
                    title = stmt.getText(2),
                    extOpenEntriesInBrowser = stmt.getInt(3) == 1,
                    extBlockedWords = stmt.getText(4),
                    extShowPreviewImages = stmt.getInt(5) == 1,
                    unreadEntries = stmt.getLong(6)
                )
            )
        }
        stmt.close()
        return res
    }

    fun selectById(id: String): Feed? {
        val stmt = conn.prepare("SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed WHERE id = ?")
        stmt.bindText(1, id)
        val feed = if (stmt.step()) statementToFeed(stmt) else null
        stmt.close()
        return feed
    }

    fun selectLinks(): List<List<Link>> {
        val res = mutableListOf<List<Link>>()
        val stmt = conn.prepare("SELECT links FROM entry")
        while (stmt.step()) {
            res.add(jsonToLinks(stmt.getText(0)))
        }
        stmt.close()
        return res
    }

    fun deleteById(id: String) {
        val stmt = conn.prepare("DELETE FROM feed WHERE id = ?")
        stmt.bindText(1, id)
        stmt.step()
        stmt.close()
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM feed")
    }

    private fun statementToFeed(stmt: SQLiteStatement): Feed {
        return Feed(
            id = stmt.getText(0),
            links = jsonToLinks(stmt.getText(1)),
            title = stmt.getText(2),
            extOpenEntriesInBrowser = stmt.getInt(3) == 1,
            extBlockedWords = stmt.getText(4),
            extShowPreviewImages = stmt.getInt(5) == 1
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

class EntrySearchQueries(private val conn: SQLiteConnection) {
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
        val stmt = conn.prepare(sql)
        stmt.bindText(1, searchQuery)
        stmt.bindText(2, searchQuery)
        stmt.bindText(3, searchQuery)
        while (stmt.step()) {
            res.add(
                SelectByQuery(
                    id = stmt.getText(0),
                    extShowPreviewImages = stmt.getInt(1) == 1,
                    extOpenGraphImageUrl = stmt.getText(2),
                    extOpenGraphImageWidth = stmt.getInt(3),
                    extOpenGraphImageHeight = stmt.getInt(4),
                    title = stmt.getText(5),
                    feedTitle = stmt.getText(6),
                    published = OffsetDateTime.parse(stmt.getText(7)),
                    summary = stmt.getText(8),
                    extRead = stmt.getInt(9) == 1,
                    extOpenEntriesInBrowser = stmt.getInt(10) == 1,
                    links = jsonToLinks(stmt.getText(11))
                )
            )
        }
        stmt.close()
        return res
    }
}
