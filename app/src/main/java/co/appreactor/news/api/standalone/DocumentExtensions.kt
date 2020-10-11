package co.appreactor.news.api.standalone

import co.appreactor.news.db.Entry
import co.appreactor.news.db.Feed
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.*

private val RSS_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z")
        .withZoneUTC().withLocale(Locale.US)

fun Document.getFeedType(): FeedType {
    if (documentElement.tagName == "feed" && documentElement.getAttribute("xmlns") == "http://www.w3.org/2005/Atom") {
        return FeedType.ATOM
    }

    if (documentElement.tagName == "rss") {
        return FeedType.RSS
    }

    return FeedType.UNKNOWN
}

fun Document.toAtomFeed(): Feed {
    val id = documentElement.getElementsByTagName("id").item(0).textContent
        ?: throw Exception("Atom channel has no id")
    val title = documentElement.getElementsByTagName("title").item(0).textContent
        ?: throw Exception("Atom channel has no title")

    var selfLink = ""
    var alternateLink = ""

    (0 until documentElement.childNodes.length)
        .mapNotNull { documentElement.childNodes.item(it) }
        .filter { it is Element && it.tagName == "link" }
        .map { it as Element }
        .forEach { link ->
            val href = link.getAttribute("href")

            when (link.getAttribute("rel")) {
                "self" -> selfLink = href
                "alternate" -> alternateLink = href
            }
        }

    return Feed(
        id = id,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
    )
}

fun Document.toAtomEntries(): List<Entry> {
    val feedId = getElementsByTagName("id").item(0).textContent ?: return emptyList()
    val entries = getElementsByTagName("entry")

    return (0 until entries.length).mapNotNull { index ->
        val entry = entries.item(index) as Element

        val id = entry.getElementsByTagName("id").item(0).textContent ?: return@mapNotNull null
        val title = entry.getElementsByTagName("title").item(0).textContent ?: return@mapNotNull null
        val link = entry.getElementsByTagName("link").item(0).textContent ?: return@mapNotNull null
        val published = entry.getElementsByTagName("published").item(0).textContent ?: return@mapNotNull null
        val updated = entry.getElementsByTagName("updated").item(0).textContent ?: return@mapNotNull null

        val author = entry.getElementsByTagName("author")?.item(0)

        val authorName = if (author != null && author is Element) {
            author.getElementsByTagName("name")?.item(0)?.textContent ?: ""
        } else {
            ""
        }

        val content = entry.getElementsByTagName("content").item(0).textContent ?: return@mapNotNull null

        Entry(
            id = id,
            feedId = feedId,
            title = title,
            link = link,
            published = published,
            updated = updated,
            authorName = authorName,
            content = content,
            enclosureLink = "",
            enclosureLinkType = "",
            viewed = false,
            viewedSynced = true,
            bookmarked = false,
            bookmarkedSynced = true,
            guidHash = "",
        )
    }
}

fun Document.toRssFeed(documentUri: String): Feed {
    val channel = documentElement.getElementsByTagName("channel").item(0) as Element
    val title = channel.getElementsByTagName("title").item(0).textContent
        ?: throw Exception("RSS channel has no title")
    val link = channel.getElementsByTagName("link").item(0).textContent
        ?: throw Exception("RSS channel has no link")

    return Feed(
        id = link,
        title = title,
        selfLink = documentUri,
        alternateLink = link,
    )
}

fun Document.toRssEntries(): List<Entry> {
    val channel = documentElement.getElementsByTagName("channel").item(0) as Element
    val feedId = channel.getElementsByTagName("link").item(0).textContent
        ?: return emptyList()
    val items = channel.getElementsByTagName("item")

    return (0 until items.length).mapNotNull { index ->
        val item = items.item(index) as Element

        val guid = item.getElementsByTagName("guid").item(0).textContent ?: return@mapNotNull null
        val title = item.getElementsByTagName("title").item(0).textContent ?: "Untitled"
        val link = item.getElementsByTagName("link").item(0).textContent ?: ""
        val pubDate = item.getElementsByTagName("pubDate").item(0).textContent ?: ""
        val author = item.getElementsByTagName("author").item(0).textContent ?: ""
        val description = item.getElementsByTagName("description").item(0).textContent ?: ""

        Entry(
            id = guid,
            feedId = feedId,
            title = title,
            link = link,
            published = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
            updated = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
            authorName = author,
            content = description,
            enclosureLink = "",
            enclosureLinkType = "",
            viewed = false,
            viewedSynced = true,
            bookmarked = false,
            bookmarkedSynced = true,
            guidHash = "",
        )
    }
}