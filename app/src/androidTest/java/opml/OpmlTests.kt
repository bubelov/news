package opml

import db.Feed
import org.junit.Assert
import org.junit.Test
import readFile

class OpmlTests {

    private val sampleElements = listOf(
        OpmlElement(
            text = "WirelessMoves",
            xmlUrl = "https://blog.wirelessmoves.com/feed",
            htmlUrl = "https://blog.wirelessmoves.com",
            type = "rss",
        ),
        OpmlElement(
            text = "Nextcloud",
            xmlUrl = "https://nextcloud.com/blogfeed",
            htmlUrl = "https://nextcloud.com",
            type = "rss",
        ),
        OpmlElement(
            text = "PINE64",
            xmlUrl = "https://www.pine64.org/feed/",
            htmlUrl = "https://www.pine64.org",
            type = "rss",
        ),
    )

    @Test
    fun readsSampleDocument() {
        val elements = readOpml(readFile("sample.opml"))
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }

    @Test
    fun writesSampleDocument() {
        val feeds = sampleElements.map {
            Feed(
                id = "",
                title = it.text,
                selfLink = it.xmlUrl,
                alternateLink = it.htmlUrl,
            )
        }

        val opml = writeOpml(feeds)
        val elements = readOpml(opml)
        Assert.assertArrayEquals(sampleElements.toTypedArray(), elements.toTypedArray())
    }
}