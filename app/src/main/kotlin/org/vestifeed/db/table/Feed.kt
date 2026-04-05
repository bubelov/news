package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.db.bindBooleanOrNull
import org.vestifeed.db.getBoolOrNull
import org.vestifeed.parser.AtomLinkRel
import kotlin.collections.forEach

const val FEED_SCHEMA = """
    CREATE TABLE feed (
        id TEXT PRIMARY KEY NOT NULL,
        links TEXT,
        title TEXT NOT NULL,
        ext_open_entries_in_browser INTEGER,
        ext_blocked_words TEXT,
        ext_show_preview_images INTEGER
    ) STRICT;
  """

typealias Feed = FeedProjection

data class FeedProjection(
    val id: String,
    val links: List<Link>,
    val title: String,
    val extOpenEntriesInBrowser: Boolean?,
    val extBlockedWords: String,
    val extShowPreviewImages: Boolean?,
)
    
class FeedQueries(private val conn: SQLiteConnection) {
    fun insertOrReplace(feeds: List<Feed>) {
        if (feeds.isEmpty()) {
            return
        }
        conn.prepare(
            """
            INSERT OR REPLACE
            INTO feed (id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images)
            VALUES (?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            feeds.forEach { feed ->
                stmt.bindText(1, feed.id)
                stmt.bindText(2, linksToJson(feed.links))
                stmt.bindText(3, feed.title)
                stmt.bindBooleanOrNull(4, feed.extOpenEntriesInBrowser)
                stmt.bindText(5, feed.extBlockedWords)
                stmt.bindBooleanOrNull(6, feed.extShowPreviewImages)
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun selectAll(): List<FeedProjection> {
        conn.prepare(
            """
            SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images
            FROM feed
            ORDER BY title;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        FeedProjection(
                            id = stmt.getText(0),
                            links = jsonToLinks(stmt.getText(1)),
                            title = stmt.getText(2),
                            extOpenEntriesInBrowser = stmt.getBoolOrNull(3),
                            extBlockedWords = stmt.getText(4),
                            extShowPreviewImages = stmt.getBoolOrNull(5),
                        )
                    )
                }
            }
        }
    }

    fun selectAllLinks(): List<List<Link>> {
        conn.prepare(
            """
            SELECT links
            FROM feed;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(jsonToLinks(stmt.getText(0)))
                }
            }
        }
    }

    fun selectById(id: String): FeedProjection? {
        conn.prepare(
            """
            SELECT id, links, title, ext_open_entries_in_browser, ext_blocked_words, ext_show_preview_images
            FROM feed
            WHERE id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, id)
            return if (stmt.step()) FeedProjection(
                id = stmt.getText(0),
                links = jsonToLinks(stmt.getText(1)),
                title = stmt.getText(2),
                extOpenEntriesInBrowser = stmt.getBoolOrNull(3),
                extBlockedWords = stmt.getText(4),
                extShowPreviewImages = stmt.getBoolOrNull(5),
            ) else null
        }
    }

    fun deleteById(id: String) {
        conn.prepare(
            """
            DELETE FROM feed
            WHERE id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, id)
            stmt.step()
        }
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM feed;")
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