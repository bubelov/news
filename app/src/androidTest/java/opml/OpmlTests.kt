package opml

import db.Feed
import org.junit.Assert
import org.junit.Test
import readFile

class OpmlTests {

    private val sampleElements = listOf(
        Outline(
            text = "WirelessMoves",
            type = "rss",
            xmlUrl = "https://blog.wirelessmoves.com/feed",
        ),
        Outline(
            text = "Nextcloud",
            type = "rss",
            xmlUrl = "https://nextcloud.com/blogfeed",
        ),
        Outline(
            text = "PINE64",
            type = "rss",
            xmlUrl = "https://www.pine64.org/feed/",
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
                id = "",
                title = it.text,
                selfLink = it.xmlUrl,
                alternateLink = "",
                openEntriesInBrowser = false,
                blockedWords = "",
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