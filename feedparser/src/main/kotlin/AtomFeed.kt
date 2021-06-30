package co.appreactor.feedparser

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

data class AtomFeed(
    val title: String,
    val selfLink: String,
    val alternateLink: String,
    val entries: Result<List<AtomEntry>>,
) : Feed()

data class AtomEntry(
    val id: String,
    val feedId: String,
    val title: String,
    val link: String,
    val published: String,
    val updated: String,
    val authorName: String,
    val content: String,
    val enclosureLink: String,
    val enclosureLinkType: String,
)

fun atomFeed(url: URL): Result<AtomFeed> {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    val document = runCatching {
        documentBuilder.parse(url.openStream())
    }.getOrElse {
        return Result.failure(it)
    }

    return atomFeed(document, url)
}

fun atomFeed(document: Document, url: URL): Result<AtomFeed> {
    val documentElement = document.documentElement

    val title = documentElement.getElementsByTagName("title").item(0).textContent
        ?: throw Exception("Atom channel has no title")

    var selfLink = url.toString()
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
        selfLink = url.toString()
    }

    return Result.success(
        AtomFeed(
            title = title,
            selfLink = selfLink,
            alternateLink = alternateLink,
            entries = atomEntries(document),
        )
    )
}

fun atomEntries(document: Document): Result<List<AtomEntry>> {
    val feedId = document.getElementsByTagName("id").item(0).textContent
        ?: return Result.failure(Exception("Feed ID is missing"))

    val entries = document.getElementsByTagName("entry")

    val parsedEntries = (0 until entries.length).mapNotNull { index ->
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

        AtomEntry(
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

    return Result.success(parsedEntries)
}