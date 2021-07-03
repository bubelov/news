package co.appreactor.feedparser

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

val RFC_822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

data class RssFeed(
    val channel: RssChannel,
) : Feed()

data class RssChannel(
    // The name of the channel. It's how people refer to your service. If you have an HTML website
    // that contains the same information as your RSS file, the title of your channel should be the
    // same as the title of your website
    val title: String,
    // The URL to the HTML website corresponding to the channel
    val link: URL,
    // Phrase or sentence describing the channel
    val description: String,
    // A channel may contain any number of <item>s. An item may represent a "story" -- much like a
    // story in a newspaper or magazine; if so its description is a synopsis of the story, and the
    // link points to the full story. An item may also be complete in itself, if so, the
    // description contains the text (entity-encoded HTML is allowed), and the link and title may
    // be omitted. All elements of an item are optional, however at least one of title or
    // description must be present
    val items: Result<List<Result<RssItem>>>
)

data class RssItem(
    // The title of the item
    val title: String?,
    // The URL of the item
    val link: URL?,
    // The item synopsis.
    val description: String?,
    // Email address of the author of the item
    val author: String?,
    // Includes the item in one or more categories
    val categories: List<RssItemCategory>,
    // URL of a page for comments relating to the item
    val comments: URL?,
    // Describes a media object that is attached to the item
    val enclosure: RssItemEnclosure?,
    // A string that uniquely identifies the item
    val guid: String?,
    // Indicates when the item was published
    val pubDate: Date?,
    // The RSS channel that the item came from
    val source: RssItemSource?,
)

data class RssItemCategory(
    // A string that identifies a categorization taxonomy
    val domain: String?,
    // Forward-slash-separated string that identifies a hierarchic location in the indicated
    // taxonomy
    val value: String,
)

data class RssItemEnclosure(
    // url says where the enclosure is located
    val url: URL,
    // length says how big it is in bytes
    val length: Long,
    // type says what its type is, a standard MIME type
    val type: String,
)

data class RssItemSource(
    // url links to the source XML
    val url: URL,
    // value is the name of the RSS channel that the item came from, derived from its <title>
    val value: String?,
)

fun rssFeed(url: URL): Result<RssFeed> {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    val document = runCatching {
        documentBuilder.parse(url.openStream())
    }.getOrElse {
        return Result.failure(it)
    }

    return rssFeed(document)
}

fun rssFeed(document: Document): Result<RssFeed> {
    val channel = document.documentElement.getElementsByTagName("channel").item(0) as Element

    val title = channel.getElementsByTagName("title")?.item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no title"))

    val rawLink = channel.getElementsByTagName("link")?.item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no link"))

    val link = runCatching {
        URI.create(rawLink).toURL()
    }.getOrElse {
        return Result.failure(Exception("Failed to parse link as URL"))
    }

    val description = channel.getElementsByTagName("description")?.item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no description"))

    return Result.success(
        RssFeed(
            channel = RssChannel(
                title = title,
                link = link,
                description = description,
                items = rssItems(document)
            ),
        )
    )
}

fun rssItems(document: Document): Result<List<Result<RssItem>>> {
    val channel = document.documentElement.getElementsByTagName("channel")?.item(0) as Element?
        ?: return Result.failure(Exception("Missing element: channel"))

    val itemElements = channel.getElementsByTagName("item")?.toList()
        ?: return Result.failure(Exception("Failed to read channel items"))

    val items: List<Result<RssItem>> = itemElements.map { element ->
        val rawLink = element.getElementsByTagName("link")?.item(0)?.textContent

        val link = if (rawLink != null) {
            runCatching {
                URI.create(rawLink).toURL()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse link as URL"))
            }
        } else {
            null
        }

        val categories = element.getElementsByTagName("category").toList().map { categoryElement ->
            RssItemCategory(
                domain = categoryElement.attributes?.getNamedItem("domain")?.textContent,
                value = categoryElement.textContent ?: "",
            )
        }

        var enclosure: RssItemEnclosure? = null

        element.getElementsByTagName("enclosure")?.item(0)?.apply {
            val rawUrl = attributes?.getNamedItem("url")?.textContent
                ?: return@map Result.failure(Exception("Enclosure URL is missing"))

            val url = runCatching {
                URI.create(rawUrl).toURL()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse enclosure URL"))
            }

            val rawLength = attributes?.getNamedItem("length")?.textContent
                ?: return@map Result.failure(Exception("Enclosure length is missing"))

            val length = runCatching {
                rawLength.toLong()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse enclosure length"))
            }

            val type = attributes?.getNamedItem("type")?.textContent
                ?: return@map Result.failure(Exception("Enclosure type is missing"))

            enclosure = RssItemEnclosure(
                url = url,
                length = length,
                type = type,
            )
        }

        val rawPubDate = element.getElementsByTagName("pubDate").item(0).textContent?.trim()

        val pubDate = if (rawPubDate != null) {
            runCatching {
                RFC_822.parse(rawPubDate)
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse pubDate as date"))
            }
        } else {
            null
        }

        Result.success(
            RssItem(
                title = element.getElementsByTagName("title")?.item(0)?.textContent,
                link = link,
                description = element.getElementsByTagName("description")?.item(0)?.textContent,
                author = element.getElementsByTagName("author")?.item(0)?.textContent,
                categories = categories,
                // TODO
                comments = null,
                enclosure = enclosure,
                guid = element.getElementsByTagName("guid")?.item(0)?.textContent,
                pubDate = pubDate,
                // TODO
                source = null,
            )
        )
    }

    return Result.success(items)
}

fun NodeList.toList(): List<Element> {
    val list = mutableListOf<Element>()

    repeat(length) {
        list += item(it) as Element
    }

    return list
}