package org.vestifeed.parser

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RFC_822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

data class RssFeed(
    // Mandatory attribute that specifies the version of RSS that the document conforms to
    val version: RssVersion,
    val channel: RssChannel,
) : Feed()

enum class RssVersion {
    RSS_2_0,
}

data class RssChannel(
    // The name of the channel. It's how people refer to your service. If you have an HTML website
    // that contains the same information as your RSS file, the title of your channel should be the
    // same as the title of your website
    val title: String,
    // The URL to the HTML website corresponding to the channel
    val link: String,
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
    val link: String?,
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
    val guid: RssItemGuid?,
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

sealed class RssItemGuid {
    data class StringGuid(val value: String) : RssItemGuid()

    // only if isPermalink = true
    data class UrlGuid(val value: URL) : RssItemGuid()
}

data class RssItemSource(
    // url links to the source XML
    val url: URL,
    // value is the name of the RSS channel that the item came from, derived from its <title>
    val value: String?,
)

fun rssFeed(document: Document): Result<RssFeed> {
    val rawVersion = document.documentElement.attributes.getNamedItem("version")?.textContent

    if (rawVersion.isNullOrBlank()) {
        return Result.failure(Exception("RSS version is missing"))
    }

    val version = when (rawVersion) {
        "2.0" -> RssVersion.RSS_2_0
        else -> return Result.failure(Exception("Unsupported RSS version: $rawVersion"))
    }

    val channel = document.documentElement.getElementsByTagName("channel").item(0) as Element

    val title = channel.getElementsByTagName("title").item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no title"))

    val link = channel.getElementsByTagName("link").item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no link"))

    val description = channel.getElementsByTagName("description").item(0)?.textContent
        ?: return Result.failure(Exception("Channel has no description"))

    return Result.success(
        RssFeed(
            version = version,
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
    val channel = document.documentElement.getElementsByTagName("channel").item(0) as Element?
        ?: return Result.failure(Exception("Missing element: channel"))

    val itemElements = channel.getElementsByTagName("item").list().filterIsInstance<Element>()

    val items: List<Result<RssItem>> = itemElements.map { element ->
        val link = element.getElementsByTagName("link").item(0)?.textContent

        val categories = element.getElementsByTagName("category")
            .list()
            .filterIsInstance<Element>()
            .map { categoryElement ->
                RssItemCategory(
                    domain = categoryElement.attributes.getNamedItem("domain")?.textContent,
                    value = categoryElement.textContent ?: "",
                )
            }

        val commentsElements =
            element.getElementsByTagName("comments").list().filterIsInstance<Element>()

        if (commentsElements.size > 1) {
            return Result.failure(
                Exception("Expected 0 or 1 comments elements but got ${commentsElements.size}"),
            )
        }

        val comments = if (commentsElements.isEmpty()) null else {
            runCatching { URI.create(commentsElements.single().textContent).toURL() }.getOrElse {
                return Result.failure(
                    Exception("Expected 0 or 1 comments elements but got ${commentsElements.size}"),
                )
            }
        }

        var enclosure: RssItemEnclosure? = null

        element.getElementsByTagName("enclosure").item(0)?.apply {
            val rawUrl = attributes.getNamedItem("url")?.textContent
                ?: return@map Result.failure(Exception("Enclosure URL is missing"))

            val url = runCatching {
                URI.create(rawUrl).toURL()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse enclosure URL"))
            }

            val rawLength = attributes.getNamedItem("length")?.textContent
                ?: return@map Result.failure(Exception("Enclosure length is missing"))

            val length = runCatching {
                rawLength.toLong()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse enclosure length"))
            }

            val type = attributes.getNamedItem("type")?.textContent
                ?: return@map Result.failure(Exception("Enclosure type is missing"))

            enclosure = RssItemEnclosure(
                url = url,
                length = length,
                type = type,
            )
        }

        val rawPubDate = element.getElementsByTagName("pubDate").item(0)?.textContent?.trim()

        val pubDate = if (rawPubDate != null) {
            runCatching {
                RFC_822.parse(rawPubDate)
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse pubDate as date"))
            }
        } else {
            null
        }

        var guid: RssItemGuid? = null

        element.getElementsByTagName("guid").item(0)?.apply {
            val permalink = attributes.getNamedItem("isPermaLink")?.textContent == "true"

            guid = if (permalink) {
                val url = runCatching {
                    URI.create(textContent).toURL()
                }.getOrElse {
                    return@map Result.failure(Exception("Failed to parse guid as URL"))
                }

                RssItemGuid.UrlGuid(url)
            } else {
                RssItemGuid.StringGuid(textContent)
            }
        }

        var source: RssItemSource? = null

        element.getElementsByTagName("source").item(0)?.apply {
            val rawUrl = attributes.getNamedItem("url")?.textContent
                ?: return@map Result.failure(Exception("Source URL is missing"))

            val url = runCatching {
                URI.create(rawUrl).toURL()
            }.getOrElse {
                return@map Result.failure(Exception("Failed to parse source URL"))
            }

            source = RssItemSource(
                url = url,
                value = textContent,
            )
        }

        Result.success(
            RssItem(
                title = element.getElementsByTagName("title").item(0)?.textContent,
                link = link,
                description = element.getElementsByTagName("description").item(0)?.textContent,
                author = element.getElementsByTagName("author").item(0)?.textContent,
                categories = categories,
                comments = comments,
                enclosure = enclosure,
                guid = guid,
                pubDate = pubDate,
                source = source,
            )
        )
    }

    return Result.success(items)
}