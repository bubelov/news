import org.junit.Assert.assertEquals
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
            .toAtomFeed(documentUrl = "https://gitlab.com/fdroid/rfp/-/issues.atom?feed_token=gdoyU2ZstimRyxzcCh4P&state=opened")
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
            assertEquals("https://tools.ietf.org/html/", id)
            assertEquals("New RFCs", title)
            assertEquals("https://foo.bar", selfLink)
            assertEquals("https://tools.ietf.org/html/", alternateLink)
        }
    }

    @Test
    fun parseDates() {
        val dates = listOf(
            "Mon, 21 Jan 2019 16:06:12 GMT",
            "Mon, 27 Jan 2020 17:55:00 EST",
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
}