package org.vestifeed.parser

import org.w3c.dom.Document
import org.w3c.dom.Element

data class AtomFeed(
    val title: String,
    val links: List<AtomLink>,
    val entries: List<AtomEntry>,
) : Feed()

data class AtomEntry(
    val id: String,
    val feedId: String,
    val title: String,
    val published: String,
    val updated: String,
    val authorName: String,
    val content: AtomEntryContent,
    val links: List<AtomLink>,
    val summary: AtomEntrySummary?,
)

data class AtomLink(
    val href: String,
    val rel: AtomLinkRel?,
    val type: String,
    val hreflang: String,
    val title: String,
    val length: Long?,
)

sealed class AtomLinkRel {
    object Alternate : AtomLinkRel()
    object Enclosure : AtomLinkRel()
    object Related : AtomLinkRel()
    object Self : AtomLinkRel()
    object Via : AtomLinkRel()
    data class Custom(val value: String) : AtomLinkRel()
}

/*
https://datatracker.ietf.org/doc/html/rfc4287#section-4.1.3

atomInlineTextContent =
  element atom:content {
     atomCommonAttributes,
     attribute type { "text" | "html" }?,
     (text)*
  }

atomInlineXHTMLContent =
  element atom:content {
     atomCommonAttributes,
     attribute type { "xhtml" },
     xhtmlDiv
  }

atomInlineOtherContent =
  element atom:content {
     atomCommonAttributes,
     attribute type { atomMediaType }?,
     (text|anyElement)*
  }

atomOutOfLineContent =
  element atom:content {
     atomCommonAttributes,
     attribute type { atomMediaType }?,
     attribute src { atomUri },
     empty
  }

atomContent = atomInlineTextContent
| atomInlineXHTMLContent
| atomInlineOtherContent
| atomOutOfLineContent
 */
data class AtomEntryContent(
    val type: AtomEntryContentType,
    val src: String,
    val text: String,
)

sealed class AtomEntryContentType {
    object Text : AtomEntryContentType()
    object Html : AtomEntryContentType()
    object Xhtml : AtomEntryContentType()
    data class Mime(val mime: String) : AtomEntryContentType()
}

data class AtomEntrySummary(
    val type: String,
    val text: String,
)

fun atomFeed(document: Document): Result<AtomFeed> {
    val documentElement = document.documentElement

    val title = documentElement.getElementsByTagName("title").item(0).textContent
        ?: return Result.failure(Exception("Channel has no title"))

    val links = documentElement.childNodes
        .list()
        .filterIsInstance<Element>()
        .filter { it.tagName == "link" }
        .map { element -> element.toAtomLink().getOrElse { return Result.failure(it) } }

    val entries = atomEntries(document).getOrElse {
        return Result.failure(it)
    }

    return Result.success(
        AtomFeed(
            title = title,
            links = links,
            entries = entries,
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

        val content: AtomEntryContent

        val contentElements = entry.getElementsByTagName("content")

        when (contentElements.length) {
            0 -> {
                /*
                TODO

                https://datatracker.ietf.org/doc/html/rfc4287#section-4.1.2

                atom:entry elements that contain no child atom:content element
                MUST contain at least one atom:link element with a rel attribute
                value of "alternate"
                 */
                content = AtomEntryContent(
                    type = AtomEntryContentType.Text,
                    src = "",
                    text = "",
                )
            }

            1 -> {
                val element = contentElements.item(0) as Element
                val rawContentType = element.getAttribute("type").trim()
                val src = element.getAttribute("src").trim()

                if (rawContentType.isEmpty() && src.isNotEmpty()) {
                    return Result.failure(Exception("Content type is missing"))
                }

                val contentType = when (rawContentType) {
                    "", "text" -> AtomEntryContentType.Text
                    "html" -> AtomEntryContentType.Html
                    "xhtml" -> AtomEntryContentType.Xhtml
                    else -> AtomEntryContentType.Mime(rawContentType)
                }

                content = AtomEntryContent(
                    type = contentType,
                    src = src,
                    text = element.textContent,
                )
            }

            else -> {
                return Result.failure(Exception("Feed entry has more than one content element"))
            }
        }

        val summary: AtomEntrySummary?

        val summaryElements = entry.getElementsByTagName("summary")

        when (summaryElements.length) {
            0 -> {
                summary = null
            }

            1 -> {
                val element = summaryElements.item(0) as Element
                val rawContentType = element.getAttribute("type").trim()

                summary = AtomEntrySummary(
                    type = rawContentType,
                    text = element.textContent,
                )
            }

            else -> {
                return Result.failure(Exception("Feed entry has more than one summary element"))
            }
        }

        val elementsWithUpdatedTag = entry.getElementsByTagName("updated")

        // > atom:entry elements MUST contain exactly one atom:updated element.
        // Source: https://tools.ietf.org/html/rfc4287
        if (elementsWithUpdatedTag.length != 1) {
            return Result.failure(Exception("atom:entry elements MUST contain exactly one atom:updated element"))
        }

        val updated = elementsWithUpdatedTag.item(0).textContent
            ?: return Result.failure(Exception("atom:updated element shouldn't be empty"))

        // > atom:entry elements MUST contain exactly one atom:updated element.
        // Source: https://tools.ietf.org/html/rfc4287
        val author = entry.getElementsByTagName("author").item(0)

        // TODO
        // atom:entry elements MUST contain one or more atom:author elements, unless the atom:entry
        // contains an atom:source element that contains an atom:author element or, in an Atom Feed
        // Document, the atom:feed element contains an atom:author element itself.
        // Source: https://tools.ietf.org/html/rfc4287
        val authorName = if (author != null && author is Element) {
            author.getElementsByTagName("name").item(0)?.textContent ?: ""
        } else {
            ""
        }

        val linkElements = entry.getElementsByTagName("link")

        val links = linkElements
            .list()
            .filterIsInstance<Element>()
            .map { element -> element.toAtomLink().getOrElse { return Result.failure(it) } }

        AtomEntry(
            id = id,
            feedId = feedId,
            title = title,
            published = updated, // TODO
            updated = updated,
            authorName = authorName,
            content = content,
            links = links,
            summary = summary,
        )
    }

    return Result.success(parsedEntries)
}

private fun Element.toAtomLink(): Result<AtomLink> {
    val rel = when (getAttribute("rel")) {
        "" -> AtomLinkRel.Alternate
        "alternate" -> AtomLinkRel.Alternate
        "enclosure" -> AtomLinkRel.Enclosure
        "related" -> AtomLinkRel.Related
        "self" -> AtomLinkRel.Self
        "via" -> AtomLinkRel.Via
        else -> AtomLinkRel.Custom(getAttribute("rel"))
    }

    val lengthAttrName = "length"

    val length = if (getAttribute(lengthAttrName).toLongOrNull() != null) {
        getAttribute(lengthAttrName).toLong()
    } else {
        null
    }

    return Result.success(
        AtomLink(
            href = getAttribute("href"),
            rel = rel,
            type = getAttribute("type"),
            hreflang = getAttribute("hreflang"),
            title = getAttribute("title"),
            length = length,
        )
    )
}