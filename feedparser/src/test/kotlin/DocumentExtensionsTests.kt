import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DocumentExtensionsTests {

    @Test
    fun getFeedType_recognizesAtom() {
        assertEquals(FeedType.ATOM, "github-curl.atom".toDocument().getFeedType())
    }

    @Test
    fun toAtomFeed() {
        "github-curl.atom".toDocument().toAtomFeed().apply {
            assertEquals("tag:github.com,2008:https://github.com/curl/curl/releases", id)
            assertEquals("Release notes from curl", title)
            assertEquals("https://github.com/curl/curl/releases.atom", selfLink)
            assertEquals("https://github.com/curl/curl/releases", alternateLink)
        }

        "kernel.atom".toDocument().toAtomFeed().apply {
            assertEquals("https://www.kernel.org/", id)
            assertEquals("The Linux Kernel Archives", title)
            assertEquals("https://www.kernel.org/feeds/all.atom.xml", selfLink)
            assertEquals("https://www.kernel.org/", alternateLink)
        }

        "theverge.atom".toDocument().toAtomFeed("https://www.theverge.com/rss/full.xml").apply {
            assertEquals("https://www.theverge.com/rss/full.xml", id)
            assertEquals("The Verge -  All Posts", title)
            assertEquals("https://www.theverge.com/rss/full.xml", selfLink)
            assertEquals("https://www.theverge.com/", alternateLink)
        }

        "youtube.atom".toDocument().toAtomFeed().apply {
            assertEquals("yt:channel:UCXuqSBlHAE6Xw-yeJA0Tunw", id)
            assertEquals("Linus Tech Tips", title)
            assertEquals(
                "https://www.youtube.com/feeds/videos.xml?channel_id=UCXuqSBlHAE6Xw-yeJA0Tunw",
                selfLink
            )
            assertEquals("https://www.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw", alternateLink)
        }

        "fdroid-issues.atom".toDocument()
            .toAtomFeed("https://gitlab.com/fdroid/rfp/-/issues.atom?feed_token=gdoyU2ZstimRyxzcCh4P&state=opened")
            .apply {
                assertEquals("https://gitlab.com/fdroid/rfp/-/issues", id)
                assertEquals("Requests For Packaging issues", title)
                assertEquals(
                    "https://gitlab.com/fdroid/rfp/-/issues.atom?feed_token=gdoyU2ZstimRyxzcCh4P&state=opened",
                    selfLink
                )
                assertEquals("https://gitlab.com/fdroid/rfp/-/issues", alternateLink)
            }
    }

    @Test
    fun toAtomEntries() {
        "github-curl.atom".toDocument().apply {
            val entries = this.toAtomEntries()
            assertEquals(10, entries.size)
        }
    }

    @Test
    fun getFeedType_recognizesRss() {
        assertEquals(FeedType.RSS, "ietf.rss".toDocument().getFeedType())
    }

    @Test
    fun toRssFeed() {
        "ietf.rss".toDocument().toRssFeed("https://foo.bar").apply {
            assertEquals("https://foo.bar", id)
            assertEquals("New RFCs", title)
            assertEquals("https://foo.bar", selfLink)
            assertEquals("https://tools.ietf.org/html/", alternateLink)
        }

        val mozilla = rssFeed("mozilla.rss", "https://blog.mozilla.org/feed/")
        val mozillaComments = rssFeed("mozilla-comments.rss", "https://blog.mozilla.org/comments/feed/")
        assertNotEquals(mozilla.id, mozillaComments.id)
    }

    @Test
    fun toRssEntries() {
        "2021-05-14-lukesmith.xyz.rss".toDocument().apply {
            val entries = this.toRssEntries()
            assertEquals(1, entries.size)
        }
    }

    @Test
    fun parseDates() {
        val dates = listOf(
            "Mon, 21 Jan 2019 16:06:12 GMT",
            "Mon, 27 Jan 2020 17:55:00 EST",
            "Sat, 13 Mar 2021 08:47:51 -0500",
        )

        dates.forEach { date ->
            println("Testing date: $date")
            val parsedDate = RFC_822.parse(date).toInstant()
            println("Parsed date: $parsedDate")
        }
    }

    private fun String.toDocument(): Document {
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return documentBuilder.parse(File("src/test/resources/$this"))
    }

    fun rssFeed(fileName: String, feedUrl: String): ParsedFeed {
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = documentBuilder.parse(File("src/test/resources/$fileName"))
        return document.toRssFeed(feedUrl)
    }
}