package co.appreactor.news.api.standalone

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DocumentExtensionsTests {

    @Test
    fun getFeedType_recognizesAtom() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(File("src/test/resources/github-curl.atom"))
        assertEquals(FeedType.ATOM, document.getFeedType())
    }

    @Test
    fun getFeedType_recognizesRss() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(File("src/test/resources/ietf.rss"))
        assertEquals(FeedType.RSS, document.getFeedType())
    }

    @Test
    fun toAtomFeed() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        File("src/test/resources/github-curl.atom").apply {
            val document = builder.parse(this)
            val feed = document.toAtomFeed()
            assertEquals("tag:github.com,2008:https://github.com/curl/curl/releases", feed.id)
            assertEquals("Release notes from curl", feed.title)
            assertEquals("https://github.com/curl/curl/releases.atom", feed.selfLink)
            assertEquals("https://github.com/curl/curl/releases", feed.alternateLink)
        }

        File("src/test/resources/kernel.atom").apply {
            val document = builder.parse(this)
            val feed = document.toAtomFeed()
            assertEquals("https://www.kernel.org/", feed.id)
            assertEquals("The Linux Kernel Archives", feed.title)
            assertEquals("https://www.kernel.org/feeds/all.atom.xml", feed.selfLink)
            assertEquals("https://www.kernel.org/", feed.alternateLink)
        }
    }
}