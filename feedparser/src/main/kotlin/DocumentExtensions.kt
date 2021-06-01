import org.w3c.dom.Document
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.*

val RFC_822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

fun Document.getFeedType(): FeedType {
    if (documentElement.tagName == "feed" && documentElement.getAttribute("xmlns") == "http://www.w3.org/2005/Atom") {
        return FeedType.ATOM
    }

    if (documentElement.tagName == "rss") {
        return FeedType.RSS
    }

    return FeedType.UNKNOWN
}

fun Document.toAtomFeed(feedUrl: String): ParsedFeed {
    val title = documentElement.getElementsByTagName("title").item(0).textContent
        ?: throw Exception("Atom channel has no title")

    var selfLink = feedUrl
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

    if (selfLink.startsWith("http") && !selfLink.startsWith("https")) {
        selfLink = selfLink.replaceFirst("http", "https")
    }

    if (!selfLink.startsWith("https")) {
        selfLink = feedUrl
    }

    return ParsedFeed(
        id = feedUrl,
        title = title,
        selfLink = selfLink,
        alternateLink = alternateLink,
    )
}

fun Document.toAtomEntries(): List<ParsedEntry> {
    val feedId = getElementsByTagName("id").item(0).textContent ?: return emptyList()
    val entries = getElementsByTagName("entry")

    return (0 until entries.length).mapNotNull { index ->
        val entry = entries.item(index) as Element

        // > atom:entry elements MUST contain exactly one atom:id element.
        // Source: https://tools.ietf.org/html/rfc4287
        val id = entry.getElementsByTagName("id").item(0).textContent ?: return@mapNotNull null

        // > atom:entry elements MUST contain exactly one atom:title element.
        // Source: https://tools.ietf.org/html/rfc4287
        val title =
            entry.getElementsByTagName("title").item(0).textContent ?: return@mapNotNull null

        var content = ""

        val contentElements = entry.getElementsByTagName("content")

        if (contentElements.length > 0) {
            content = contentElements.item(0).textContent ?: ""
        }

        val linkElements = entry.getElementsByTagName("link")

        // > atom:entry elements that contain no child atom:content element MUST contain at least
        // > one atom:link element with a rel attribute value of "alternate".
        // Source: https://tools.ietf.org/html/rfc4287
        if (contentElements.length == 0 && linkElements.length == 0) {
            return@mapNotNull null
        }

        var link = ""

        if (linkElements.length > 0) {
            link = linkElements.item(0).attributes.getNamedItem("href").textContent ?: ""
        }

        // > atom:entry elements MUST contain exactly one atom:updated element.
        // Source: https://tools.ietf.org/html/rfc4287
        val updated =
            entry.getElementsByTagName("updated").item(0).textContent ?: return@mapNotNull null

        // > atom:entry elements MUST contain exactly one atom:updated element.
        // Source: https://tools.ietf.org/html/rfc4287
        val author = entry.getElementsByTagName("author")?.item(0)

        // TODO
        // atom:entry elements MUST contain one or more atom:author elements, unless the atom:entry
        // contains an atom:source element that contains an atom:author element or, in an Atom Feed
        // Document, the atom:feed element contains an atom:author element itself.
        // Source: https://tools.ietf.org/html/rfc4287
        val authorName = if (author != null && author is Element) {
            author.getElementsByTagName("name")?.item(0)?.textContent ?: ""
        } else {
            ""
        }

        ParsedEntry(
            id = id,
            feedId = feedId,
            title = title,
            link = link,
            published = updated, // TODO
            updated = updated,
            authorName = authorName,
            content = content,
            enclosureLink = "",
            enclosureLinkType = "",
        )
    }
}

fun Document.toRssFeed(feedUrl: String): ParsedFeed {
    val channel = documentElement.getElementsByTagName("channel").item(0) as Element
    val title = channel.getElementsByTagName("title").item(0).textContent
        ?: throw Exception("RSS channel has no title")
    val link = channel.getElementsByTagName("link").item(0).textContent
        ?: throw Exception("RSS channel has no link")

    return ParsedFeed(
        id = feedUrl,
        title = title,
        selfLink = feedUrl,
        alternateLink = link,
    )
}

fun Document.toRssEntries(): List<ParsedEntry> {
    val channel = documentElement.getElementsByTagName("channel").item(0) as Element
    val feedId = channel.getElementsByTagName("link").item(0).textContent
        ?: return emptyList()
    val items = channel.getElementsByTagName("item")

    return (0 until items.length).mapNotNull { index ->
        val item = items.item(index) as Element

        val guid = item.getElementsByTagName("guid").item(0)?.textContent
            ?: item.getElementsByTagName("link").item(0)?.textContent
            ?: return@mapNotNull null

        val title = item.getElementsByTagName("title").item(0).textContent ?: "Untitled"
        val link = item.getElementsByTagName("link").item(0)?.textContent ?: ""
        val pubDate = (item.getElementsByTagName("pubDate").item(0).textContent ?: "").trim()

        val author = if (item.getElementsByTagName("author").length > 0) {
            item.getElementsByTagName("author").item(0).textContent ?: ""
        } else {
            ""
        }

        val descriptionElements = item.getElementsByTagName("description")

        val description = if (descriptionElements.length > 0) {
            descriptionElements.item(0).textContent ?: ""
        } else {
            ""
        }

        val enclosureLink = if (item.getElementsByTagName("enclosure").length > 0) {
            item.getElementsByTagName("enclosure")
                .item(0).attributes.getNamedItem("url").textContent ?: ""
        } else {
            ""
        }

        val enclosureLinkType = if (item.getElementsByTagName("enclosure").length > 0) {
            item.getElementsByTagName("enclosure")
                .item(0).attributes.getNamedItem("type").textContent ?: ""
        } else {
            ""
        }

        val parsedDate = RFC_822.parse(pubDate).toInstant()

        ParsedEntry(
            id = guid,
            feedId = feedId,
            title = title,
            link = link,
            published = parsedDate.toString(),
            updated = parsedDate.toString(),
            authorName = author,
            content = description,
            enclosureLink = enclosureLink,
            enclosureLinkType = enclosureLinkType,
        )
    }
}