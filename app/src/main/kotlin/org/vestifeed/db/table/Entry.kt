package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import org.vestifeed.db.bindTextOrNull
import org.vestifeed.db.getTextOrNull
import java.time.OffsetDateTime

object EntrySchema {
    const val TABLE_NAME = "entry"

    override fun toString(): String {
        return """
            CREATE TABLE $TABLE_NAME (
                ${Columns.ContentType} TEXT,
                ${Columns.ContentSrc} TEXT,
                ${Columns.ContentText} TEXT,
                ${Columns.Summary} TEXT,
                ${Columns.Id} TEXT PRIMARY KEY NOT NULL,
                ${Columns.FeedId} TEXT NOT NULL,
                ${Columns.Title} TEXT NOT NULL,
                ${Columns.Published} TEXT NOT NULL,
                ${Columns.Updated} TEXT NOT NULL,
                ${Columns.AuthorName} TEXT NOT NULL,
                ${Columns.ExtRead} INTEGER NOT NULL,
                ${Columns.ExtReadSynced} INTEGER NOT NULL,
                ${Columns.ExtBookmarked} INTEGER NOT NULL,
                ${Columns.ExtBookmarkedSynced} INTEGER NOT NULL,
                ${Columns.ExtCommentsUrl} TEXT NOT NULL,
                ${Columns.ExtOgImageChecked} INTEGER NOT NULL,
                ${Columns.ExtOgImageUrl} TEXT NOT NULL,
                ${Columns.ExtOgImageWidth} INTEGER NOT NULL,
                ${Columns.ExtOgImageHeight} INTEGER NOT NULL
            ) STRICT;
        """
    }

    enum class Columns(val sqlName: String) {
        ContentType("content_type"),
        ContentSrc("content_src"),
        ContentText("content_text"),
        Summary("summary"),
        Id("id"),
        FeedId("feed_id"),
        Title("title"),
        Published("published"),
        Updated("updated"),
        AuthorName("author_name"),
        ExtRead("ext_read"),
        ExtReadSynced("ext_read_synced"),
        ExtBookmarked("ext_bookmarked"),
        ExtBookmarkedSynced("ext_bookmarked_synced"),
        ExtCommentsUrl("ext_comments_url"),
        ExtOgImageChecked("ext_og_image_checked"),
        ExtOgImageUrl("ext_og_image_url"),
        ExtOgImageWidth("ext_og_image_width"),
        ExtOgImageHeight("ext_og_image_height");

        override fun toString() = sqlName
    }
}

typealias Entry = EntryProjection

data class EntryProjection(
    val contentType: String?,
    val contentSrc: String?,
    val contentText: String?,
    val summary: String?,
    val id: String,
    val feedId: String,
    val title: String,
    val published: OffsetDateTime,
    val updated: OffsetDateTime,
    val authorName: String,
    val extRead: Boolean,
    val extReadSynced: Boolean,
    val extBookmarked: Boolean,
    val extBookmarkedSynced: Boolean,
    val extCommentsUrl: String,
    val extOpenGraphImageChecked: Boolean,
    val extOpenGraphImageUrl: String,
    val extOpenGraphImageWidth: Int,
    val extOpenGraphImageHeight: Int,
) {
    companion object {
        val columns: String
            get() {
                return EntrySchema.Columns.entries.joinToString(",") { it.sqlName }
            }

        fun fromStatement(stmt: SQLiteStatement): EntryProjection {
            return EntryProjection(
                contentType = stmt.getTextOrNull(0),
                contentSrc = stmt.getTextOrNull(1),
                contentText = stmt.getTextOrNull(2),
                summary = stmt.getTextOrNull(3),
                id = stmt.getText(4),
                feedId = stmt.getText(5),
                title = stmt.getText(6),
                published = OffsetDateTime.parse(stmt.getText(7)),
                updated = OffsetDateTime.parse(stmt.getText(8)),
                authorName = stmt.getText(9),
                extRead = stmt.getInt(10) == 1,
                extReadSynced = stmt.getInt(11) == 1,
                extBookmarked = stmt.getInt(12) == 1,
                extBookmarkedSynced = stmt.getInt(13) == 1,
                extCommentsUrl = stmt.getText(14),
                extOpenGraphImageChecked = stmt.getInt(15) == 1,
                extOpenGraphImageUrl = stmt.getText(16),
                extOpenGraphImageWidth = stmt.getInt(17),
                extOpenGraphImageHeight = stmt.getInt(18)
            )
        }
    }
}

fun Entry.withoutContent(): EntryQueries.EntryWithoutContent {
    return EntryQueries.EntryWithoutContent(
        summary = summary,
        id = id,
        feedId = feedId,
        title = title,
        published = published,
        updated = updated,
        authorName = authorName,
        extRead = extRead,
        extReadSynced = extReadSynced,
        extBookmarked = extBookmarked,
        extBookmarkedSynced = extBookmarkedSynced,
        extCommentsUrl = extCommentsUrl,
        extOpenGraphImageChecked = extOpenGraphImageChecked,
        extOpenGraphImageUrl = extOpenGraphImageUrl,
        extOpenGraphImageWidth = extOpenGraphImageWidth,
        extOpenGraphImageHeight = extOpenGraphImageHeight,
    )
}

class EntryQueries(private val conn: SQLiteConnection) {
    fun insertOrReplace(entries: List<Entry>) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO
            ${EntrySchema.TABLE_NAME} (${EntryProjection.columns})
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            entries.forEach { entry ->
                stmt.bindTextOrNull(1, entry.contentType)
                stmt.bindTextOrNull(2, entry.contentSrc)
                stmt.bindTextOrNull(3, entry.contentText)
                stmt.bindTextOrNull(4, entry.summary)
                stmt.bindText(5, entry.id)
                stmt.bindText(6, entry.feedId)
                stmt.bindText(7, entry.title)
                stmt.bindText(8, entry.published.toString())
                stmt.bindText(9, entry.updated.toString())
                stmt.bindText(10, entry.authorName)
                stmt.bindInt(11, if (entry.extRead) 1 else 0)
                stmt.bindInt(12, if (entry.extReadSynced) 1 else 0)
                stmt.bindInt(13, if (entry.extBookmarked) 1 else 0)
                stmt.bindInt(14, if (entry.extBookmarkedSynced) 1 else 0)
                stmt.bindText(15, entry.extCommentsUrl)
                stmt.bindInt(16, if (entry.extOpenGraphImageChecked) 1 else 0)
                stmt.bindText(17, entry.extOpenGraphImageUrl)
                stmt.bindInt(18, entry.extOpenGraphImageWidth)
                stmt.bindInt(19, entry.extOpenGraphImageHeight)
                stmt.step()
                stmt.reset()
            }
        }
    }

    data class ShortEntry(
        val published: OffsetDateTime,
        val title: String,
    )

    fun selectAllPublishedAndTitle(): List<ShortEntry> {
        conn.prepare(
            """
            SELECT ${EntrySchema.Columns.Published}, ${EntrySchema.Columns.Title} 
            FROM ${EntrySchema.TABLE_NAME} 
            ORDER BY ${EntrySchema.Columns.Published} DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        ShortEntry(
                            published = OffsetDateTime.parse(stmt.getText(0)),
                            title = stmt.getText(1),
                        )
                    )
                }
            }

        }
    }

    fun selectById(entryId: String): Entry? {
        conn.prepare(
            """
            SELECT ${EntryProjection.columns}
            FROM ${EntrySchema.TABLE_NAME}
            WHERE ${EntrySchema.Columns.Id} = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, entryId)
            return if (stmt.step()) EntryProjection.fromStatement(stmt) else null
        }
    }

    data class EntriesAdapterRow(
        val id: String,
        val feedId: String,
        val extBookmarked: Boolean,
        val extShowPreviewImages: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
        val title: String,
        val feedTitle: String,
        val published: OffsetDateTime,
        val summary: String,
        val extRead: Boolean,
        val extOpenEntriesInBrowser: Boolean,
    )

    fun selectByFeedId(feedId: String): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.feed_id = ?
            ORDER BY e.published DESC;   
        """
        ).use { stmt ->
            stmt.bindText(1, feedId)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectUnread(): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read = 0 AND e.ext_bookmarked = 0
            ORDER BY e.published DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectBookmarked(): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.*, f.title as feed_title, f.ext_show_preview_images, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_bookmarked = 1
            ORDER BY e.published DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectCount(): Long {
        conn.prepare("SELECT COUNT(*) FROM entry;").use { stmt ->
            return if (stmt.step()) stmt.getLong(0) else 0L
        }
    }

    fun selectMaxId(): String? {
        conn.prepare("SELECT MAX(CAST(id AS INTEGER)) FROM entry;").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
    }

    fun selectMaxUpdated(): String? {
        conn.prepare("SELECT MAX(updated) FROM entry;").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
    }

    fun updateReadAndReadSynced(id: String, extRead: Boolean, extReadSynced: Boolean) {
        conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = ? WHERE id = ?;")
            .use { stmt ->
                stmt.bindInt(1, if (extRead) 1 else 0)
                stmt.bindInt(2, if (extReadSynced) 1 else 0)
                stmt.bindText(3, id)
                stmt.step()
            }
    }

    fun updateBookmarkedAndBookmarkedSynced(
        id: String,
        extBookmarked: Boolean,
        extBookmarkedSynced: Boolean
    ) {
        conn.prepare("UPDATE entry SET ext_bookmarked = ?, ext_bookmarked_synced = ? WHERE id = ?;")
            .use { stmt ->
                stmt.bindInt(1, if (extBookmarked) 1 else 0)
                stmt.bindInt(2, if (extBookmarkedSynced) 1 else 0)
                stmt.bindText(3, id)
                stmt.step()
            }
    }

    data class EntryWithoutContent(
        val summary: String?,
        val id: String,
        val feedId: String,
        val title: String,
        val published: OffsetDateTime,
        val updated: OffsetDateTime,
        val authorName: String,
        val extRead: Boolean,
        val extReadSynced: Boolean,
        val extBookmarked: Boolean,
        val extBookmarkedSynced: Boolean,
        val extCommentsUrl: String,
        val extOpenGraphImageChecked: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
    )

    fun selectByReadSynced(extReadSynced: Boolean): List<EntryWithoutContent> {
        conn.prepare(
            """
            SELECT 
                summary,
                id,
                feed_id, 
                title, 
                published, 
                updated, 
                author_name,
                ext_read, 
                ext_read_synced,
                ext_bookmarked,
                ext_bookmarked_synced,
                ext_comments_url,
                ext_og_image_checked,
                ext_og_image_url,
                ext_og_image_width,
                ext_og_image_height
            FROM entry
            WHERE ext_read_synced = ?
            ORDER BY published DESC;
            """
        ).use { stmt ->
            stmt.bindInt(1, if (extReadSynced) 1 else 0)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
    }

    fun selectByBookmarkedSynced(extBookmarkedSynced: Boolean): List<EntryWithoutContent> {
        conn.prepare(
            """
            SELECT
                summary,
                id,
                feed_id,
                title,
                published,
                updated,
                author_name,
                ext_read,
                ext_read_synced,
                ext_bookmarked,
                ext_bookmarked_synced,
                ext_comments_url,
                ext_og_image_checked,
                ext_og_image_url,
                ext_og_image_width,
                ext_og_image_height
            FROM entry
            WHERE ext_bookmarked_synced = ?
            ORDER BY published DESC;
            """
        ).use { stmt ->
            stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
    }

    fun updateOgImageChecked(extOgImageChecked: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_og_image_checked = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun updateOgImage(
        extOgImageUrl: String,
        extOgImageWidth: Long,
        extOgImageHeight: Long,
        id: String
    ) {
        conn.prepare("UPDATE entry SET ext_og_image_url = ?, ext_og_image_width = ?, ext_og_image_height = ?, ext_og_image_checked = 1 WHERE id = ?;")
            .use { stmt ->
                stmt.bindText(1, extOgImageUrl)
                stmt.bindLong(2, extOgImageWidth)
                stmt.bindLong(3, extOgImageHeight)
                stmt.bindText(4, id)
                stmt.step()
            }
    }

    fun updateReadSynced(extReadSynced: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_read_synced = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extReadSynced) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun updateBookmarkedSynced(extBookmarkedSynced: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_bookmarked_synced = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun deleteByFeedId(feedId: String) {
        conn.prepare("DELETE FROM entry WHERE feed_id = ?").use { stmt ->
            stmt.bindText(1, feedId)
            stmt.step()
        }
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM entry")
    }

    fun selectByOgImageChecked(extOgImageChecked: Boolean, limit: Long): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        conn.prepare(
            """
            SELECT summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_og_image_checked = ? ORDER BY published DESC LIMIT ?
        """
        ).use { stmt ->
            stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
            stmt.bindLong(2, limit)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
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
        )
    }

    private fun statementToEntryWithoutContent(stmt: SQLiteStatement): EntryWithoutContent {
        return EntryWithoutContent(
            summary = stmt.getTextOrNull(0),
            id = stmt.getTextOrNull(1) ?: "",
            feedId = stmt.getTextOrNull(2) ?: "",
            title = stmt.getTextOrNull(3) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(4)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            updated = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(5)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            authorName = stmt.getTextOrNull(6) ?: "",
            extRead = stmt.getInt(7) == 1,
            extReadSynced = stmt.getInt(8) == 1,
            extBookmarked = stmt.getInt(9) == 1,
            extBookmarkedSynced = stmt.getInt(10) == 1,
            extCommentsUrl = stmt.getTextOrNull(11) ?: "",
            extOpenGraphImageChecked = stmt.getInt(12) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(13) ?: "",
            extOpenGraphImageWidth = stmt.getInt(14),
            extOpenGraphImageHeight = stmt.getInt(15)
        )
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
    )

    fun selectByQuery(query: String): List<SelectByQuery> {
        val searchQuery = "%$query%"
        val sql = """
            SELECT e.id, f.ext_show_preview_images, e.ext_og_image_url, e.ext_og_image_width,
                   e.ext_og_image_height, e.title, f.title as feed_title, e.published,
                   e.summary, e.ext_read, f.ext_open_entries_in_browser
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
                )
            )
        }
        stmt.close()
        return res
    }
}