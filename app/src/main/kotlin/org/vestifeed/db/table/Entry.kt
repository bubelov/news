package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.db.bindTextOrNull
import org.vestifeed.db.getTextOrNull
import org.vestifeed.parser.AtomLinkRel
import java.time.OffsetDateTime

object EntrySchema {
    const val TABLE_NAME = "entry"

    override fun toString(): String {
        return """
            CREATE TABLE $TABLE_NAME (
                ${Columns.ContentType} TEXT,
                ${Columns.ContentSrc} TEXT,
                ${Columns.ContentText} TEXT,
                ${Columns.Links} TEXT,
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
            );
        """
    }

    enum class Columns(val sqlName: String) {
        ContentType("content_type"),
        ContentSrc("content_src"),
        ContentText("content_text"),
        Links("links"),
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
    val links: List<Link>,
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
                extCommentsUrl = stmt.getText(15),
                extOpenGraphImageChecked = stmt.getInt(16) == 1,
                extOpenGraphImageUrl = stmt.getText(17),
                extOpenGraphImageWidth = stmt.getInt(18),
                extOpenGraphImageHeight = stmt.getInt(19)
            )
        }
    }
}

data class Link(
    val feedId: String?,
    val entryId: String?,
    val href: HttpUrl,
    val rel: AtomLinkRel?,
    val type: String?,
    val hreflang: String?,
    val title: String?,
    val length: Long?,
    val extEnclosureDownloadProgress: Double?,
    val extCacheUri: String?,
)

fun Entry.withoutContent(): EntryQueries.EntryWithoutContent {
    return EntryQueries.EntryWithoutContent(
        links = links,
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
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
                stmt.bindText(16, entry.extCommentsUrl)
                stmt.bindInt(17, if (entry.extOpenGraphImageChecked) 1 else 0)
                stmt.bindText(18, entry.extOpenGraphImageUrl)
                stmt.bindInt(19, entry.extOpenGraphImageWidth)
                stmt.bindInt(20, entry.extOpenGraphImageHeight)
                stmt.step()
                stmt.reset()
            }
        }
    }

    data class ShortEntry(
        val links: List<Link>,
        val published: OffsetDateTime,
        val title: String,
    )

    fun selectAllLinksPublishedAndTitle(): List<ShortEntry> {
        conn.prepare(
            """
            SELECT ${EntrySchema.Columns.Links}, ${EntrySchema.Columns.Published}, ${EntrySchema.Columns.Title} 
            FROM ${EntrySchema.TABLE_NAME} 
            ORDER BY ${EntrySchema.Columns.Published} DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        ShortEntry(
                            links = jsonToLinks(stmt.getText(0)),
                            published = OffsetDateTime.parse(stmt.getText(1)),
                            title = stmt.getText(2),
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

    fun selectByIds(ids: List<String>): List<Entry> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        conn.prepare(
            """
            SELECT ${EntryProjection.columns}
            FROM ${EntrySchema.TABLE_NAME}
            WHERE ${EntrySchema.Columns.Id} IN ($placeholders);
            """
        ).use { stmt ->
            ids.forEachIndexed { index, id ->
                stmt.bindText(index + 1, id)
            }
            return buildList {
                while (stmt.step()) {
                    add(EntryProjection.fromStatement(stmt))
                }
            }
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
        val links: List<Link>,
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
        val links: List<Link>,
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
                links,
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
                links,
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

    fun selectLinksById(id: String): List<Link>? {
        conn.prepare("SELECT links FROM entry WHERE id = ?;").use { stmt ->
            stmt.bindText(1, id)
            return if (stmt.step()) jsonToLinks(stmt.getText(0)) else null
        }
    }

    fun selectAllLinks(): List<List<Link>> {
        conn.prepare("SELECT links FROM entry;").use { stmt ->
            return buildList {
                add(jsonToLinks(stmt.getText(0)))
            }
        }
    }

    fun updateLinks(id: String, links: List<Link>) {
        conn.prepare("UPDATE entry SET links = ? WHERE id = ?;").use { stmt ->
            stmt.bindText(1, linksToJson(links))
            stmt.bindText(2, id)
            stmt.step()
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
            SELECT links, summary, id, feed_id, title, published, updated, author_name,
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
            extCommentsUrl = stmt.getTextOrNull(12) ?: "",
            extOpenGraphImageChecked = stmt.getInt(13) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(14) ?: "",
            extOpenGraphImageWidth = stmt.getInt(15),
            extOpenGraphImageHeight = stmt.getInt(16)
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
        val links: List<Link>
    )

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

private fun linksToJson(links: List<Link>): String {
    val array = links.map { link ->
        val length = link.length?.toString() ?: "null"
        val extEnclosureDownloadProgress = link.extEnclosureDownloadProgress?.toString() ?: "null"
        val relName = when (link.rel) {
            is AtomLinkRel.Alternate -> "Alternate"
            is AtomLinkRel.Enclosure -> "Enclosure"
            is AtomLinkRel.Self -> "Self"
            is AtomLinkRel.Related -> "Related"
            else -> ""
        }
        """{"feedId":"${link.feedId ?: ""}","entryId":"${link.entryId ?: ""}","href":"${link.href}","rel":"$relName","type":"${link.type ?: ""}","hreflang":"${link.hreflang ?: ""}","title":"${link.title ?: ""}","length":$length,"extEnclosureDownloadProgress":$extEnclosureDownloadProgress,"extCacheUri":"${link.extCacheUri ?: ""}"}"""
    }
    return "[${array.joinToString(",")}]"
}

private fun jsonToLinks(json: String?): List<Link> {
    if (json.isNullOrBlank()) return emptyList()
    val array = com.google.gson.JsonParser.parseString(json).asJsonArray
    return array.mapNotNull { element ->
        val obj = element.asJsonObject
        val relStr = obj.get("rel")?.asString ?: return@mapNotNull null
        val href = obj.get("href")?.asString ?: return@mapNotNull null
        val parsedRel: AtomLinkRel = when (relStr) {
            "Alternate" -> AtomLinkRel.Alternate
            "Enclosure" -> AtomLinkRel.Enclosure
            "Self" -> AtomLinkRel.Self
            "Related" -> AtomLinkRel.Related
            else -> AtomLinkRel.Alternate
        }
        val parsedUrl = href.toHttpUrlOrNull() ?: return@mapNotNull null
        Link(
            feedId = obj.get("feedId")?.asString?.ifEmpty { null },
            entryId = obj.get("entryId")?.asString?.ifEmpty { null },
            href = parsedUrl,
            rel = parsedRel,
            type = obj.get("type")?.asString?.ifEmpty { null },
            hreflang = obj.get("hreflang")?.asString?.ifEmpty { null },
            title = obj.get("title")?.asString?.ifEmpty { null },
            length = obj.get("length")?.let { if (it.isJsonNull) null else it.asLong },
            extEnclosureDownloadProgress = obj.get("extEnclosureDownloadProgress")
                ?.let { if (it.isJsonNull) null else it.asDouble },
            extCacheUri = obj.get("extCacheUri")?.asString?.ifEmpty { null }
        )
    }
}