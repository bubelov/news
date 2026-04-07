package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import org.vestifeed.db.bindTextOrNull
import org.vestifeed.db.getTextOrNull
import org.vestifeed.parser.AtomLinkRel

const val LINK_SCHEMA = """
    CREATE TABLE link (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        href TEXT NOT NULL,
        rel TEXT,
        type TEXT,
        hreflang TEXT,
        title TEXT,
        length TEXT,
        feed_id TEXT REFERENCES feed(id),
        entry_id TEXT REFERENCES entry(id),
        ext_enclosure_download_progress REAL,
        ext_cache_uri TEXT,
        UNIQUE(feed_id, href, rel),
        UNIQUE(entry_id, href, rel),
        CHECK ((feed_id IS NULL) <> (entry_id IS NULL))
    ) STRICT;
"""

data class Link(
    // meta
    val id: Long?,
    val feedId: String?,
    val entryId: String?,
    // core rfc fields
    val href: String,
    val rel: AtomLinkRel?,
    val type: String?,
    val hreflang: String?,
    val title: String?,
    val length: Long?,
    // extensions
    val extEnclosureDownloadProgress: Double?,
    val extCacheUri: String?,
)

class LinkQueries(private val conn: SQLiteConnection) {
    fun insertForFeed(feedId: String, links: List<Link>) {
        conn.prepare(
            """
            INSERT INTO link (
                feed_id,
                href,
                rel,
                type,
                hreflang,
                title,
                length,
                ext_enclosure_download_progress,
                ext_cache_uri
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            links.forEach { link ->
                stmt.bindText(1, feedId)
                stmt.bindText(2, link.href)
                stmt.bindTextOrNull(3, relToString(link.rel))
                stmt.bindTextOrNull(4, link.type)
                stmt.bindTextOrNull(5, link.hreflang)
                stmt.bindTextOrNull(6, link.title)
                stmt.bindTextOrNull(7, link.length?.toString())
                stmt.bindTextOrNull(8, link.extEnclosureDownloadProgress?.toString())
                stmt.bindTextOrNull(9, link.extCacheUri)
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun insertForEntry(entryId: String, links: List<Link>) {
        conn.prepare(
            """
            INSERT INTO link (
                entry_id,
                href,
                rel,
                type,
                hreflang,
                title,
                length,
                ext_enclosure_download_progress,
                ext_cache_uri
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            links.forEach { link ->
                stmt.bindText(1, entryId)
                stmt.bindText(2, link.href)
                stmt.bindTextOrNull(3, relToString(link.rel))
                stmt.bindTextOrNull(4, link.type)
                stmt.bindTextOrNull(5, link.hreflang)
                stmt.bindTextOrNull(6, link.title)
                stmt.bindTextOrNull(7, link.length?.toString())
                stmt.bindTextOrNull(8, link.extEnclosureDownloadProgress?.toString())
                stmt.bindTextOrNull(9, link.extCacheUri)
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun selectByFeedId(feedId: String): List<Link> {
        conn.prepare(
            """
            SELECT
                id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link
            WHERE feed_id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, feedId)
            return buildList {
                while (stmt.step()) {
                    add(stmt.toLink(id = stmt.getLong(0), feedId = feedId, entryId = null))
                }
            }
        }
    }

    fun selectByEntryId(entryId: String): List<Link> {
        conn.prepare(
            """
            SELECT
                id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link
            WHERE entry_id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, entryId)
            return buildList {
                while (stmt.step()) {
                    add(stmt.toLink(id = stmt.getLong(0), feedId = null, entryId = entryId))
                }
            }
        }
    }

    fun selectAllByFeedId(feedIds: List<String>): Map<String, List<Link>> {
        if (feedIds.isEmpty()) return emptyMap()
        val placeholders = feedIds.joinToString(",") { "?" }
        conn.prepare(
            """
            SELECT
                id,
                feed_id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link
            WHERE feed_id IN ($placeholders);
            """
        ).use { stmt ->
            feedIds.forEachIndexed { index, id ->
                stmt.bindText(index + 1, id)
            }
            val result = mutableMapOf<String, MutableList<Link>>()
            while (stmt.step()) {
                val feedId = stmt.getText(1)
                val link = stmt.toLink(id = stmt.getLong(0), feedId = feedId, entryId = null)
                result.getOrPut(feedId) { mutableListOf() }.add(link)
            }
            return result
        }
    }

    fun selectAllByEntryId(entryIds: List<String>): Map<String, List<Link>> {
        if (entryIds.isEmpty()) return emptyMap()
        val placeholders = entryIds.joinToString(",") { "?" }
        conn.prepare(
            """
            SELECT
                id,
                entry_id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link
            WHERE entry_id IN ($placeholders);
            """
        ).use { stmt ->
            entryIds.forEachIndexed { index, id ->
                stmt.bindText(index + 1, id)
            }
            val result = mutableMapOf<String, MutableList<Link>>()
            while (stmt.step()) {
                val entryId = stmt.getText(1)
                val link = stmt.toLink(id = stmt.getLong(0), feedId = null, entryId = entryId)
                result.getOrPut(entryId) { mutableListOf() }.add(link)
            }
            return result
        }
    }

    fun updateEnclosureProgress(linkId: Long, progress: Double?, cacheUri: String?) {
        conn.prepare(
            """
            UPDATE link
            SET ext_enclosure_download_progress = ?, ext_cache_uri = ?
            WHERE id = ?;
            """
        ).use { stmt ->
            stmt.bindTextOrNull(1, progress?.toString())
            stmt.bindTextOrNull(2, cacheUri)
            stmt.bindLong(3, linkId)
            stmt.step()
        }
    }

    fun deleteByFeedId(feedId: String) {
        conn.prepare("DELETE FROM link WHERE feed_id = ?;")
            .use { stmt ->
                stmt.bindText(1, feedId)
                stmt.step()
            }
    }

    fun deleteByEntryId(entryId: String) {
        conn.prepare("DELETE FROM link WHERE entry_id = ?;")
            .use { stmt ->
                stmt.bindText(1, entryId)
                stmt.step()
            }
    }

    fun selectAll(): List<Link> {
        conn.prepare(
            """
            SELECT
                id,
                feed_id,
                entry_id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        stmt.toLink(
                            id = stmt.getLong(0),
                            feedId = stmt.getTextOrNull(1),
                            entryId = stmt.getTextOrNull(2)
                        )
                    )
                }
            }
        }
    }

    fun selectByEntryIdAndHref(entryId: String, href: String): Link? {
        conn.prepare(
            """
            SELECT
                id,
                feed_id,
                entry_id,
                ext_enclosure_download_progress,
                ext_cache_uri,
                href,
                rel,
                type,
                hreflang,
                title,
                length
            FROM link
            WHERE entry_id = ? AND href = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, entryId)
            stmt.bindText(2, href)
            return if (stmt.step()) {
                stmt.toLink(
                    id = stmt.getLong(0),
                    feedId = stmt.getTextOrNull(1),
                    entryId = stmt.getTextOrNull(2)
                )
            } else {
                null
            }
        }
    }

    private fun SQLiteStatement.toLink(id: Long, feedId: String?, entryId: String?): Link {
        return Link(
            id = id,
            feedId = feedId,
            entryId = entryId,
            href = getText(getColumnNames().indexOf("href")),
            rel = stringToRel(getTextOrNull(getColumnNames().indexOf("rel"))),
            type = getTextOrNull(getColumnNames().indexOf("type")),
            hreflang = getTextOrNull(getColumnNames().indexOf("hreflang")),
            title = getTextOrNull(getColumnNames().indexOf("title")),
            length = getTextOrNull(getColumnNames().indexOf("length"))?.toLongOrNull(),
            extEnclosureDownloadProgress = getTextOrNull(getColumnNames().indexOf("ext_enclosure_download_progress"))?.toDoubleOrNull(),
            extCacheUri = getTextOrNull(getColumnNames().indexOf("ext_cache_uri")),
        )
    }

    private fun relToString(rel: AtomLinkRel?): String? {
        return when (rel) {
            is AtomLinkRel.Alternate -> "Alternate"
            is AtomLinkRel.Enclosure -> "Enclosure"
            is AtomLinkRel.Self -> "Self"
            is AtomLinkRel.Related -> "Related"
            is AtomLinkRel.Via -> "Via"
            is AtomLinkRel.Custom -> rel.value
            null -> null
        }
    }

    private fun stringToRel(str: String?): AtomLinkRel? {
        if (str == null) return null
        return when (str) {
            "Alternate" -> AtomLinkRel.Alternate
            "Enclosure" -> AtomLinkRel.Enclosure
            "Self" -> AtomLinkRel.Self
            "Related" -> AtomLinkRel.Related
            "Via" -> AtomLinkRel.Via
            else -> AtomLinkRel.Custom(str)
        }
    }
}
