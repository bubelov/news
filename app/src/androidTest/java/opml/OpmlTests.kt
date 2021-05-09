package opml

import db.Feed
import org.junit.Assert
import org.junit.Test
import readFile
import java.util.*

class OpmlTests {

    private val sampleElements = listOf(
        Outline(
            text = "WirelessMoves",
            type = "rss",
            xmlUrl = "https://blog.wirelessmoves.com/feed",
            openEntriesInBrowser = true,
            blockedWords = "abc",
        ),
        Outline(
            text = "Nextcloud",
            type = "rss",
            xmlUrl = "https://nextcloud.com/blogfeed",
            openEntriesInBrowser = false,
            blockedWords = "",
        ),
        Outline(
            text = "PINE64",
            type = "rss",
            xmlUrl = "https://www.pine64.org/feed/",
            openEntriesInBrowser = true,
            blockedWords = "xyz",
        ),
    )

    @Test
    fun readsSampleDocument() {
        val elements = importOpml(readFile("sample.opml"))
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun writesSampleDocument() {
        val feeds = sampleElements.map {
            Feed(
                id = UUID.randomUUID().toString(),
                title = it.text,
                selfLink = it.xmlUrl,
                alternateLink = "",
                openEntriesInBrowser = it.openEntriesInBrowser,
                blockedWords = it.blockedWords,
            )
        }

        val opml = exportOpml(feeds)
        val elements = importOpml(opml)
        Assert.assertTrue(opml.lines().size > 1)
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun readsMozillaOpml() {
        val elements = importOpml(readFile("mozilla.opml"))
        Assert.assertEquals(2, elements.size)
    }
}