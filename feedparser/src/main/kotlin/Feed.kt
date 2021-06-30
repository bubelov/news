package co.appreactor.feedparser

import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

sealed class Feed

fun feed(url: URL): Result<Feed> {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    val document = runCatching {
        documentBuilder.parse(url.openStream())
    }.getOrElse {
        return Result.failure(it)
    }

    return when (feedType(document)) {
        FeedType.ATOM -> atomFeed(document, url)
        FeedType.RSS -> rssFeed(document)
        FeedType.UNKNOWN -> Result.failure(Exception("Unknown feed type"))
    }
}