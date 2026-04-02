package org.vestifeed.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.OffsetDateTime

class Database(driver: SQLiteDriver, val path: String) {

    private val conn = driver.open(path)

    val feed = FeedQueries(conn)
    val entry = EntryQueries(conn)
    val conf = ConfQueries(conn)

    init {
        migrate()
    }

    private fun migrate() {
        val stmt = conn.prepare("SELECT user_version FROM pragma_user_version")
        val version = if (stmt.step()) stmt.getInt(0) else 0

        if (version == 0) {
            conn.execSQL(FeedQueries.SCHEMA)
            conn.execSQL(EntryQueries.SCHEMA)
            conn.execSQL(ConfQueries.SCHEMA)
            conn.execSQL("PRAGMA user_version=1")
        }
    }

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
}

class EntryQueries(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
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
        );
        """
    }

    fun insertOrReplace(entries: List<Entry>) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO entry (
                content_type, content_src, content_text, links, summary, id, feed_id, title,
                published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked,
                ext_bookmarked_synced, ext_nc_guid_hash, ext_comments_url, ext_og_image_checked,
                ext_og_image_url, ext_og_image_width, ext_og_image_height
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        ).use { stmt ->
            entries.forEach { entry ->
                stmt.bindTextOrNull(1, entry.contentType)
                stmt.bindTextOrNull(2, entry.contentSrc)
                stmt.bindTextOrNull(3, entry.contentText)
                stmt.bindText(4, linksToJson(entry.links))
                stmt.bindTextOrNull(5, entry.summary)
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
                stmt.reset()
            }
        }
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

    fun selectUnreadByFeedId(feedId: String): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.feed_id = ? AND e.ext_read = 0
            ORDER BY e.published DESC;   
        """.trimIndent()
        ).use { stmt ->
            val res = mutableListOf<EntriesAdapterRow>()
            stmt.bindText(1, feedId)
            while (stmt.step()) {
                res.add(statementToEntriesAdapterRow(stmt))
            }
            return res
        }
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

    fun selectUnread(): List<EntriesAdapterRow> {
        val query = """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read = 0 AND e.ext_bookmarked = 0
            ORDER BY e.published DESC
        """.trimIndent()

        val res = mutableListOf<EntriesAdapterRow>()
        val stmt = conn.prepare(query)
        while (stmt.step()) {
            res.add(statementToEntriesAdapterRow(stmt))
        }
        stmt.close()
        return res
    }

    fun selectByReadAndBookmarked(
        extRead: Boolean,
        extBookmarked: Boolean
    ): List<EntriesAdapterRow> {
        val query = """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read = ? AND e.ext_bookmarked = ?
            ORDER BY e.published DESC
        """.trimIndent()

        val res = mutableListOf<EntriesAdapterRow>()
        val stmt = conn.prepare(query)
        stmt.bindInt(1, if (extRead) 1 else 0)
        stmt.bindInt(2, if (extBookmarked) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntriesAdapterRow(stmt))
        }
        stmt.close()
        return res
    }

    fun selectBookmarked(): List<EntriesAdapterRow> {
        val query = """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_bookmarked = 1
            ORDER BY e.published DESC
        """.trimIndent()

        val res = mutableListOf<EntriesAdapterRow>()
        val stmt = conn.prepare(query)
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
        conn.prepare("SELECT MAX(CAST(id AS INTEGER)) FROM entry").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
    }

    fun selectMaxUpdated(): String? {
        conn.prepare("SELECT MAX(updated) FROM entry").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
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
        val stmt =
            conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = 0 WHERE ext_read != ? AND feed_id = ?")
        stmt.bindInt(1, if (read) 1 else 0)
        stmt.bindInt(2, readInt)
        stmt.bindText(3, feedId)
        stmt.step()
        stmt.close()
    }

    fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        val readInt = if (read) 0 else 1
        val bookmarkedInt = if (bookmarked) 1 else 0
        val stmt =
            conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = 0 WHERE ext_read != ? AND ext_bookmarked = ?")
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

    fun updateBookmarkedAndBookmaredSynced(
        id: String,
        extBookmarked: Boolean,
        extBookmarkedSynced: Boolean
    ) {
        val stmt =
            conn.prepare("UPDATE entry SET ext_bookmarked = ?, ext_bookmarked_synced = ? WHERE id = ?")
        stmt.bindInt(1, if (extBookmarked) 1 else 0)
        stmt.bindInt(2, if (extBookmarkedSynced) 1 else 0)
        stmt.bindText(3, id)
        stmt.step()
        stmt.close()
    }

    fun selectByReadSynced(extReadSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val stmt = conn.prepare(
            """
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_read_synced = ? ORDER BY published DESC
        """
        )
        stmt.bindInt(1, if (extReadSynced) 1 else 0)
        while (stmt.step()) {
            res.add(statementToEntryWithoutContent(stmt))
        }
        stmt.close()
        return res
    }

    fun selectByBookmarkedSynced(extBookmarkedSynced: Boolean): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        val stmt = conn.prepare(
            """
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_bookmarked_synced = ? ORDER BY published DESC
        """
        )
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

    fun updateOgImage(
        extOgImageUrl: String,
        extOgImageWidth: Long,
        extOgImageHeight: Long,
        id: String
    ) {
        val stmt =
            conn.prepare("UPDATE entry SET ext_og_image_url = ?, ext_og_image_width = ?, ext_og_image_height = ?, ext_og_image_checked = 1 WHERE id = ?")
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
        val stmt = conn.prepare(
            """
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_nc_guid_hash, ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_og_image_checked = ? ORDER BY published DESC LIMIT ?
        """
        )
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
            contentType = stmt.getTextOrNull(0),
            contentSrc = stmt.getTextOrNull(1),
            contentText = stmt.getTextOrNull(2),
            links = jsonToLinks(stmt.getTextOrNull(3)),
            summary = stmt.getTextOrNull(4),
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

    private fun SQLiteStatement.getTextOrNull(index: Int): String? {
        return if (isNull(index)) null else getText(index)
    }

    private fun statementToEntriesAdapterRow(stmt: SQLiteStatement): EntriesAdapterRow {
        return EntriesAdapterRow(
            id = stmt.getTextOrNull(getColumnIndex(stmt, "id")) ?: "",
            feedId = stmt.getTextOrNull(getColumnIndex(stmt, "feed_id")) ?: "",
            extBookmarked = stmt.getInt(getColumnIndex(stmt, "ext_bookmarked")) == 1,
            extShowPreviewImages = stmt.getInt(
                getColumnIndex(
                    stmt,
                    "ext_show_preview_images"
                )
            ) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(getColumnIndex(stmt, "ext_og_image_url"))
                ?: "",
            extOpenGraphImageWidth = stmt.getInt(getColumnIndex(stmt, "ext_og_image_width")),
            extOpenGraphImageHeight = stmt.getInt(getColumnIndex(stmt, "ext_og_image_height")),
            title = stmt.getTextOrNull(getColumnIndex(stmt, "title")) ?: "",
            feedTitle = stmt.getTextOrNull(getColumnIndex(stmt, "feed_title")) ?: "",
            published = runCatching {
                OffsetDateTime.parse(
                    stmt.getTextOrNull(
                        getColumnIndex(
                            stmt,
                            "published"
                        )
                    )
                )
            }.getOrDefault(OffsetDateTime.now()),
            summary = stmt.getTextOrNull(getColumnIndex(stmt, "summary")) ?: "",
            extRead = stmt.getInt(getColumnIndex(stmt, "ext_read")) == 1,
            extOpenEntriesInBrowser = stmt.getInt(
                getColumnIndex(
                    stmt,
                    "ext_open_entries_in_browser"
                )
            ) == 1,
            links = jsonToLinks(stmt.getTextOrNull(getColumnIndex(stmt, "links")))
        )
    }

    private fun statementToSelectByQuery(stmt: SQLiteStatement): SelectByQuery {
        return SelectByQuery(
            id = stmt.getTextOrNull(0) ?: "",
            extShowPreviewImages = stmt.getInt(1) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(2) ?: "",
            extOpenGraphImageWidth = stmt.getInt(3),
            extOpenGraphImageHeight = stmt.getInt(4),
            title = stmt.getTextOrNull(5) ?: "",
            feedTitle = stmt.getTextOrNull(6) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(7)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            summary = stmt.getTextOrNull(8) ?: "",
            extRead = stmt.getInt(9) == 1,
            extOpenEntriesInBrowser = stmt.getInt(10) == 1,
            links = jsonToLinks(stmt.getTextOrNull(11))
        )
    }

    private fun statementToEntryWithoutContent(stmt: SQLiteStatement): EntryWithoutContent {
        return EntryWithoutContent(
            links = jsonToLinks(stmt.getTextOrNull(0)),
            summary = stmt.getTextOrNull(1),
            id = stmt.getTextOrNull(2) ?: "",
            feedId = stmt.getTextOrNull(3) ?: "",
            title = stmt.getTextOrNull(4) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(5)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            updated = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(6)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            authorName = stmt.getTextOrNull(7) ?: "",
            extRead = stmt.getInt(8) == 1,
            extReadSynced = stmt.getInt(9) == 1,
            extBookmarked = stmt.getInt(10) == 1,
            extBookmarkedSynced = stmt.getInt(11) == 1,
            extNextcloudGuidHash = stmt.getTextOrNull(12) ?: "",
            extCommentsUrl = stmt.getTextOrNull(13) ?: "",
            extOpenGraphImageChecked = stmt.getInt(14) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(15) ?: "",
            extOpenGraphImageWidth = stmt.getInt(16),
            extOpenGraphImageHeight = stmt.getInt(17)
        )
    }

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

class FeedQueries(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE IF NOT EXISTS feed (
            id TEXT PRIMARY KEY NOT NULL,
            links TEXT,
            title TEXT NOT NULL,
            ext_open_entries_in_browser INTEGER,
            ext_blocked_words TEXT NOT NULL,
            ext_show_preview_images INTEGER
        );
        """
    }

    fun insertOrReplace(feeds: List<Feed>) {
        if (feeds.isEmpty()) {
            return
        }

        conn.prepare(
            """
            INSERT OR REPLACE INTO feed (
                id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images
            ) VALUES (?, ?, ?, ?, ?, ?)
        """
        ).use { stmt ->
            feeds.forEach { feed ->
                stmt.bindText(1, feed.id)
                stmt.bindText(2, linksToJson(feed.links))
                stmt.bindText(3, feed.title)
                if (feed.extOpenEntriesInBrowser != null) {
                    stmt.bindInt(4, if (feed.extOpenEntriesInBrowser) 1 else 0)
                } else {
                    stmt.bindNull(4)
                }
                stmt.bindText(5, feed.extBlockedWords)
                if (feed.extShowPreviewImages != null) {
                    stmt.bindInt(6, if (feed.extShowPreviewImages) 1 else 0)
                } else {
                    stmt.bindNull(6)
                }
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun selectAll(): List<Feed> {
        val res = mutableListOf<Feed>()
        val stmt =
            conn.prepare("SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed ORDER BY title")
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
        val stmt =
            conn.prepare("SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images FROM feed WHERE id = ?")
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
            extOpenEntriesInBrowser = if (stmt.isNull(3)) null else stmt.getInt(3) == 1,
            extBlockedWords = stmt.getText(4),
            extShowPreviewImages = if (stmt.isNull(5)) null else stmt.getInt(5) == 1
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

@PublishedApi
internal fun linksToJson(links: List<Link>): String {
    return links.joinToString(",") { link ->
        val length = link.length?.toString() ?: "null"
        val extEnclosureDownloadProgress = link.extEnclosureDownloadProgress?.toString() ?: "null"
        val relName = when (link.rel) {
            is org.vestifeed.parser.AtomLinkRel.Alternate -> "Alternate"
            is org.vestifeed.parser.AtomLinkRel.Enclosure -> "Enclosure"
            is org.vestifeed.parser.AtomLinkRel.Self -> "Self"
            is org.vestifeed.parser.AtomLinkRel.Related -> "Related"
            else -> ""
        }
        """{"feedId":"${link.feedId ?: ""}","entryId":"${link.entryId ?: ""}","href":"${link.href}","rel":"$relName","type":"${link.type ?: ""}","hreflang":"${link.hreflang ?: ""}","title":"${link.title ?: ""}","length":$length,"extEnclosureDownloadProgress":$extEnclosureDownloadProgress,"extCacheUri":"${link.extCacheUri ?: ""}"}"""
    }
}

@PublishedApi
internal fun jsonToLinks(json: String?): List<Link> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val links = mutableListOf<Link>()
        val regex =
            """\{"feedId":"([^"]*)","entryId":"([^"]*)","href":"([^"]*)","rel":"([^"]*)","type":"([^"]*)","hreflang":"([^"]*)","title":"([^"]*)","length":([^,]*),"extEnclosureDownloadProgress":([^,]*),"extCacheUri":"([^"]*)"\}""".toRegex()
        regex.findAll(json).forEach { match ->
            val (feedId, entryId, href, rel, type, hreflang, title, length, extEnclosureDownloadProgress, extCacheUri) = match.destructured
            val parsedRel: org.vestifeed.parser.AtomLinkRel = when (rel) {
                "Alternate" -> org.vestifeed.parser.AtomLinkRel.Alternate
                "Enclosure" -> org.vestifeed.parser.AtomLinkRel.Enclosure
                "Self" -> org.vestifeed.parser.AtomLinkRel.Self
                "Related" -> org.vestifeed.parser.AtomLinkRel.Related
                else -> org.vestifeed.parser.AtomLinkRel.Alternate
            }
            val parsedUrl = if (href.startsWith("http")) {
                href.toHttpUrlOrNull() ?: return@forEach
            } else {
                return@forEach
            }
            links.add(
                Link(
                    feedId = feedId.ifEmpty { null },
                    entryId = entryId.ifEmpty { null },
                    href = parsedUrl,
                    rel = parsedRel,
                    type = type.ifEmpty { null },
                    hreflang = hreflang.ifEmpty { null },
                    title = title.ifEmpty { null },
                    length = if (length == "null") null else length.toLongOrNull(),
                    extEnclosureDownloadProgress = if (extEnclosureDownloadProgress == "null") null else extEnclosureDownloadProgress.toDoubleOrNull(),
                    extCacheUri = extCacheUri.ifEmpty { null }
                )
            )
        }
        links
    } catch (e: Exception) {
        emptyList()
    }


}

class ConfQueries(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE IF NOT EXISTS conf (
            backend TEXT NOT NULL,
            miniflux_server_url TEXT NOT NULL,
            miniflux_server_trust_self_signed_certs INTEGER NOT NULL,
            miniflux_server_token TEXT NOT NULL,
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
        );
        """

        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"
        const val BACKEND_NEXTCLOUD = "nextcloud"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"
    }

    fun insert(conf: Conf) {
        val stmt = conn.prepare(
            """
            INSERT OR REPLACE INTO conf (
                backend, miniflux_server_url, miniflux_server_trust_self_signed_certs,
                miniflux_server_token, nextcloud_server_url,
                nextcloud_server_trust_self_signed_certs, nextcloud_server_username,
                nextcloud_server_password, initial_sync_completed, last_entries_sync_datetime,
                show_read_entries, sort_order, show_preview_images, crop_preview_images,
                mark_scrolled_entries_as_read, sync_on_startup, sync_in_background,
                background_sync_interval_millis, use_built_in_browser, show_preview_text,
                synced_on_startup
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        )
        stmt.bindText(1, conf.backend)
        stmt.bindText(2, conf.minifluxServerUrl)
        stmt.bindInt(3, if (conf.minifluxServerTrustSelfSignedCerts) 1 else 0)
        stmt.bindText(4, conf.minifluxServerToken)
        stmt.bindText(5, conf.nextcloudServerUrl)
        stmt.bindInt(6, if (conf.nextcloudServerTrustSelfSignedCerts) 1 else 0)
        stmt.bindText(7, conf.nextcloudServerUsername)
        stmt.bindText(8, conf.nextcloudServerPassword)
        stmt.bindInt(9, if (conf.initialSyncCompleted) 1 else 0)
        stmt.bindText(10, conf.lastEntriesSyncDatetime)
        stmt.bindInt(11, if (conf.showReadEntries) 1 else 0)
        stmt.bindText(12, conf.sortOrder)
        stmt.bindInt(13, if (conf.showPreviewImages) 1 else 0)
        stmt.bindInt(14, if (conf.cropPreviewImages) 1 else 0)
        stmt.bindInt(15, if (conf.markScrolledEntriesAsRead) 1 else 0)
        stmt.bindInt(16, if (conf.syncOnStartup) 1 else 0)
        stmt.bindInt(17, if (conf.syncInBackground) 1 else 0)
        stmt.bindLong(18, conf.backgroundSyncIntervalMillis)
        stmt.bindInt(19, if (conf.useBuiltInBrowser) 1 else 0)
        stmt.bindInt(20, if (conf.showPreviewText) 1 else 0)
        stmt.bindInt(21, if (conf.syncedOnStartup) 1 else 0)
        stmt.step()
        stmt.close()
    }

    fun select(): Conf {
        val stmt = conn.prepare(
            """
            SELECT
                backend,
                miniflux_server_url,
                miniflux_server_trust_self_signed_certs,
                miniflux_server_token,
                nextcloud_server_url,
                nextcloud_server_trust_self_signed_certs,
                nextcloud_server_username,
                nextcloud_server_password,
                initial_sync_completed,
                last_entries_sync_datetime,
                show_read_entries,
                sort_order,
                show_preview_images,
                crop_preview_images,
                mark_scrolled_entries_as_read,
                sync_on_startup,
                sync_in_background,
                background_sync_interval_millis,
                use_built_in_browser,
                show_preview_text,
                synced_on_startup
            FROM conf
        """
        )
        if (!stmt.step()) {
            stmt.close()
            return Conf(
                backend = "",
                minifluxServerUrl = "",
                minifluxServerTrustSelfSignedCerts = false,
                minifluxServerToken = "",
                nextcloudServerUrl = "",
                nextcloudServerTrustSelfSignedCerts = false,
                nextcloudServerUsername = "",
                nextcloudServerPassword = "",
                initialSyncCompleted = false,
                lastEntriesSyncDatetime = "",
                showReadEntries = false,
                sortOrder = SORT_ORDER_DESCENDING,
                showPreviewImages = true,
                cropPreviewImages = true,
                markScrolledEntriesAsRead = false,
                syncOnStartup = true,
                syncInBackground = true,
                backgroundSyncIntervalMillis = 10800000,
                useBuiltInBrowser = true,
                showPreviewText = true,
                syncedOnStartup = false,
            )
        }
        return Conf(
            backend = stmt.getText(0),
            minifluxServerUrl = stmt.getText(1),
            minifluxServerTrustSelfSignedCerts = stmt.getInt(2) == 1,
            minifluxServerToken = stmt.getText(3),
            nextcloudServerUrl = stmt.getText(4),
            nextcloudServerTrustSelfSignedCerts = stmt.getInt(5) == 1,
            nextcloudServerUsername = stmt.getText(6),
            nextcloudServerPassword = stmt.getText(7),
            initialSyncCompleted = stmt.getInt(8) == 1,
            lastEntriesSyncDatetime = stmt.getText(9),
            showReadEntries = stmt.getInt(10) == 1,
            sortOrder = stmt.getText(11),
            showPreviewImages = stmt.getInt(12) == 1,
            cropPreviewImages = stmt.getInt(13) == 1,
            markScrolledEntriesAsRead = stmt.getInt(14) == 1,
            syncOnStartup = stmt.getInt(15) == 1,
            syncInBackground = stmt.getInt(16) == 1,
            backgroundSyncIntervalMillis = stmt.getLong(17),
            useBuiltInBrowser = stmt.getInt(18) == 1,
            showPreviewText = stmt.getInt(19) == 1,
            syncedOnStartup = stmt.getInt(20) == 1,
        )
    }

    fun update(newConf: (Conf) -> Conf) {
        val oldConf = select()
        val newConf = newConf(oldConf)

        conn.execSQL("BEGIN TRANSACTION")
        try {
            delete()
            insert(newConf)
            conn.execSQL("COMMIT")
        } catch (e: Exception) {
            conn.execSQL("ROLLBACK")
            throw e
        }
    }

    fun delete() {
        conn.execSQL("DELETE FROM conf")
    }
}