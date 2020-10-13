package co.appreactor.news.api.standalone

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

    private fun String.toDocument(): Document {
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return documentBuilder.parse(File("src/test/resources/$this"))
    }
}