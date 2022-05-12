package opml

import db.Feed
import db.Link
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpmlTests {

    private val sampleElements = listOf(
        Outline(
            text = "WirelessMoves",
            type = "rss",
            xmlUrl = "https://blog.wirelessmoves.com/feed",
            openEntriesInBrowser = true,
            blockedWords = "abc",
            showPreviewImages = false,
        ),
        Outline(
            text = "Nextcloud",
            type = "rss",
            xmlUrl = "https://nextcloud.com/blogfeed",
            openEntriesInBrowser = false,
            blockedWords = "",
            showPreviewImages = true,
        ),
        Outline(
            text = "PINE64",
            type = "rss",
            xmlUrl = "https://www.pine64.org/feed/",
            openEntriesInBrowser = true,
            blockedWords = "xyz",
            showPreviewImages = null,
        ),
    )

    @Test
    fun readsSampleDocument() {
        val elements = importOpml(readFile("sample.opml"))
        assertContentEquals(sampleElements, elements)
    }

    @Test
    fun writesSampleDocument() {
        val feedsWithLinks = sampleElements.map {
            val feedId = UUID.randomUUID().toString()

            val selfLink = Link(
                feedId = feedId,
                entryId = null,
                href = it.xmlUrl.toHttpUrl(),
                rel = "self",
                type = null,
                hreflang = null,
                title = it.text,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )

            val feed = Feed(
                id = UUID.randomUUID().toString(),
                title = it.text,
                openEntriesInBrowser = it.openEntriesInBrowser,
                blockedWords = it.blockedWords,
                showPreviewImages = it.showPreviewImages,
            )

            Pair(feed, listOf(selfLink))
        }

        val opml = exportOpml(feedsWithLinks)
        val elements = importOpml(opml)
        assertTrue(opml.lines().size > 1)
        assertContentEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun readNestedOpml() {
        val elements = importOpml(readFile("nested.opml"))
        assertEquals(6, elements.size)
    }

    @Test
    fun readsMozillaOpml() {
        val elements = importOpml(readFile("mozilla.opml"))
        assertEquals(2, elements.size)
    }

    private fun readFile(path: String) = javaClass.getResourceAsStream(path)!!.readTextAndClose()

    private fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
        return this.bufferedReader(charset).use { it.readText() }
    }
}