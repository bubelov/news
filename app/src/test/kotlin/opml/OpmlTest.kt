package opml

import co.appreactor.feedk.AtomLinkRel
import db.Feed
import db.Link
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpmlTest {

    private val sampleElements = listOf(
        OpmlOutline(
            text = "WirelessMoves",
            outlines = emptyList(),
            xmlUrl = "https://blog.wirelessmoves.com/feed",
            htmlUrl = "https://blog.wirelessmoves.com/",
            extOpenEntriesInBrowser = true,
            extShowPreviewImages = false,
            extBlockedWords = "abc",
        ),
        OpmlOutline(
            text = "Nextcloud",
            outlines = emptyList(),
            xmlUrl = "https://nextcloud.com/blogfeed",
            htmlUrl = "https://nextcloud.com/",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = true,
        ),
        OpmlOutline(
            text = "PINE64",
            outlines = emptyList(),
            xmlUrl = "https://www.pine64.org/feed/",
            htmlUrl = "https://www.pine64.org/",
            extOpenEntriesInBrowser = true,
            extBlockedWords = "xyz",
            extShowPreviewImages = null,
        ),
    )

    @Test
    fun readsSampleDocument() {
        val doc = readFile("sample.opml").toOpml()
        assertContentEquals(sampleElements, doc.outlines)
    }

    @Test
    fun writesSampleDocument() {
        val feeds = sampleElements.map {
            val feedId = UUID.randomUUID().toString()

            val selfLink = Link(
                feedId = feedId,
                entryId = null,
                href = it.xmlUrl!!.toHttpUrl(),
                rel = AtomLinkRel.Self,
                type = null,
                hreflang = null,
                title = it.text,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )

            val alternateLink = Link(
                feedId = feedId,
                entryId = null,
                href = it.htmlUrl!!.toHttpUrl(),
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = it.text,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )

            val feed = Feed(
                id = UUID.randomUUID().toString(),
                links = listOf(selfLink, alternateLink),
                title = it.text,
                ext_open_entries_in_browser = it.extOpenEntriesInBrowser!!,
                ext_blocked_words = it.extBlockedWords!!,
                ext_show_preview_images = it.extShowPreviewImages,
            )

            feed
        }

        val outlines = feeds.map { feed ->
            OpmlOutline(
                text = feed.title,
                outlines = emptyList(),
                xmlUrl = feed.links.first { it.rel is AtomLinkRel.Self }.href.toString(),
                htmlUrl = feed.links.first { it.rel is AtomLinkRel.Alternate }.href.toString(),
                extOpenEntriesInBrowser = feed.ext_open_entries_in_browser,
                extBlockedWords = feed.ext_blocked_words,
                extShowPreviewImages = feed.ext_show_preview_images,
            )
        }

        var opmlDocument = OpmlDocument(
            version = OpmlVersion.V_2_0,
            outlines = outlines,
        )

        assertTrue(opmlDocument.toXmlDocument().toPrettyString().lines().size > 1)

        opmlDocument = opmlDocument.toXmlDocument().toPrettyString().toOpml()

        assertContentEquals(sampleElements.toTypedArray(), opmlDocument.outlines.toTypedArray())
    }

    @Test
    fun readNestedOpml() {
        val document = readFile("nested.opml").toOpml()
        assertEquals(6, document.leafOutlines().size)
    }

    @Test
    fun readsMozillaOpml() {
        val document = readFile("mozilla.opml").toOpml()
        assertEquals(2, document.outlines.size)
    }

    private fun readFile(path: String) = javaClass.getResourceAsStream(path)!!.readTextAndClose()

    private fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
        return this.bufferedReader(charset).use { it.readText() }
    }
}