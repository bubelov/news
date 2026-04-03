package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.db.Link
import kotlin.collections.forEach

object FeedSchema {
    const val TABLE_NAME = "feed"

    override fun toString(): String {
        return """
            CREATE TABLE $TABLE_NAME (
                ${Columns.Id} TEXT PRIMARY KEY NOT NULL,
                ${Columns.Links} TEXT,
                ${Columns.Title} TEXT NOT NULL,
                ${Columns.ExtOpenEntriesInBrowser} INTEGER,
                ${Columns.ExtBlockedWords} TEXT,
                ${Columns.ExtShowPreviewImages} INTEGER
            );
        """.trimIndent()
    }

    enum class Columns(val sqlName: String) {
        Id("id"),
        Links("links"),
        Title("title"),
        ExtOpenEntriesInBrowser("ext_open_entries_in_browser"),
        ExtBlockedWords("ext_blocked_words"),
        ExtShowPreviewImages("ext_show_preview_images");

        override fun toString() = sqlName
    }
}

typealias Feed = FeedProjection

data class FeedProjection(
    val id: String,
    val links: List<Link>,
    val title: String,
    val extOpenEntriesInBrowser: Boolean?,
    val extBlockedWords: String,
    val extShowPreviewImages: Boolean?,
) {
    companion object {
        val columns: String
            get() {
                return FeedSchema.Columns.entries.joinToString(",") { it.sqlName }
            }

        fun fromStatement(stmt: SQLiteStatement): FeedProjection {
            return FeedProjection(
                id = stmt.getText(0),
                links = jsonToLinks(stmt.getText(1)),
                title = stmt.getText(2),
                extOpenEntriesInBrowser = if (stmt.isNull(3)) null else stmt.getInt(3) == 1,
                extBlockedWords = stmt.getText(4),
                extShowPreviewImages = if (stmt.isNull(5)) null else stmt.getInt(5) == 1
            )
        }
    }
}

class FeedQueries(private val conn: SQLiteConnection) {
    fun insertOrReplace(feeds: List<Feed>) {
        if (feeds.isEmpty()) {
            return
        }

        conn.prepare(
            """
            INSERT OR REPLACE
            INTO ${FeedSchema.TABLE_NAME} (${FeedProjection.columns})
            VALUES (?, ?, ?, ?, ?, ?);
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

    fun selectAll(): List<FeedProjection> {
        conn.prepare(
            """
            SELECT ${FeedProjection.columns}
            FROM ${FeedSchema.TABLE_NAME}
            ORDER BY ${FeedSchema.Columns.Title};
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(FeedProjection.fromStatement(stmt))
                }
            }
        }
    }

    data class SelectAllWithUnreadEntryCount(
        val id: String,
        val links: List<Link>,
        val title: String,
        val extOpenEntriesInBrowser: Boolean,
        val extBlockedWords: String,
        val extShowPreviewImages: Boolean,
        val unreadEntries: Long,
    )

    fun selectAllWithUnreadEntryCount(): List<SelectAllWithUnreadEntryCount> {
        conn.prepare(
            """
            SELECT f.*, COUNT(e.id) as unread_entries
            FROM ${FeedSchema.TABLE_NAME} f
            LEFT JOIN entry e ON e.feed_id = f.${FeedSchema.Columns.Id} AND e.ext_read = 0 AND e.ext_bookmarked = 0
            GROUP BY f.${FeedSchema.Columns.Id}
            ORDER BY f.${FeedSchema.Columns.Title};   
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        SelectAllWithUnreadEntryCount(
                            id = stmt.getText(0),
                            links = jsonToLinks(stmt.getText(1)),
                            title = stmt.getText(2),
                            extOpenEntriesInBrowser = stmt.getInt(3) == 1,
                            extBlockedWords = stmt.getText(4),
                            extShowPreviewImages = stmt.getInt(5) == 1,
                            unreadEntries = stmt.getLong(6),
                        )
                    )
                }
            }
        }
    }

    fun selectAllLinks(): List<List<Link>> {
        conn.prepare(
            """
            SELECT ${FeedSchema.Columns.Links}
            FROM ${FeedSchema.TABLE_NAME};
            """
        ).use { stmt ->
            return buildList {
                add(jsonToLinks(stmt.getText(0)))
            }
        }
    }

    fun selectById(id: String): FeedProjection? {
        conn.prepare(
            """
            SELECT ${FeedProjection.columns}
            FROM ${FeedSchema.TABLE_NAME}
            WHERE id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, id)
            return if (stmt.step()) FeedProjection.fromStatement(stmt) else null
        }
    }

    fun deleteById(id: String) {
        conn.prepare(
            """
            DELETE FROM ${FeedSchema.TABLE_NAME}
            WHERE ${FeedSchema.Columns.Id} = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, id)
            stmt.step()
        }
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM ${FeedSchema.TABLE_NAME};")
    }
}

// todo refactor
private fun linksToJson(links: List<Link>): String {
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

// todo refactor
private fun jsonToLinks(json: String?): List<Link> {
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