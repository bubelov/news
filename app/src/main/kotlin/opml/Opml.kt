package opml

import db.Feed
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private object Symbols {
    const val OPML = "opml"
    const val VERSION = "version"
    const val HEAD = "head"
    const val TITLE = "title"
    const val BODY = "body"
    const val OUTLINE = "outline"
    const val TEXT = "text"
    const val TYPE = "type"
    const val XML_URL = "xmlUrl"
    const val NEWS_NAMESPACE_PREFIX = "news"
    const val NEWS_NAMESPACE_URL = "https://appreactor.co/news"
    const val OPEN_ENTRIES_IN_BROWSER = "openEntriesInBrowser"
    const val BLOCKED_WORDS = "blockedWords"
    const val SHOW_PREVIEW_IMAGES = "showPreviewImages"
}

fun importOpml(xml: String): List<Outline> {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val document = documentBuilder.parse(xml.byteInputStream())

    val outlines = buildList {
        val nodes = document.getElementsByTagName("outline")
        addAll((0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>())
    }

    val leafOutlines = outlines.filter { !it.hasChildNodes() }

    return leafOutlines.map {
        val showPreviewImages =
            it.getAttribute("${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.SHOW_PREVIEW_IMAGES}")

        Outline(
            text = it.getAttribute(Symbols.TEXT),
            type = it.getAttribute(Symbols.TYPE),
            xmlUrl = it.getAttribute(Symbols.XML_URL),
            openEntriesInBrowser = it.getAttribute("${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.OPEN_ENTRIES_IN_BROWSER}")
                .toBoolean(),
            blockedWords = it.getAttribute("${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.BLOCKED_WORDS}"),
            showPreviewImages = when (showPreviewImages) {
                "true" -> true
                "false" -> false
                else -> null
            },
        )
    }
}

fun exportOpml(feeds: List<Feed>): String {
    val builderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val document = builderFactory.newDocumentBuilder().newDocument()

    val opmlElement = document.createElement(Symbols.OPML).apply {
        setAttribute(
            "xmlns:${Symbols.NEWS_NAMESPACE_PREFIX}",
            Symbols.NEWS_NAMESPACE_URL,
        )

        setAttribute(Symbols.VERSION, "2.0")
    }

    document.appendChild(opmlElement)

    val headElement = document.createElement(Symbols.HEAD)
    opmlElement.appendChild(headElement)

    val titleElement = document.createElement(Symbols.TITLE).apply {
        textContent = "Subscriptions"
    }

    headElement.appendChild(titleElement)

    val bodyElement = document.createElement(Symbols.BODY)
    opmlElement.appendChild(bodyElement)

    feeds.forEach { feed ->
        bodyElement.appendChild(document.createElement(Symbols.OUTLINE).apply {
            setAttribute(Symbols.TEXT, feed.title)
            setAttribute(Symbols.TYPE, "rss")
            setAttribute(Symbols.XML_URL, feed.selfLink)

            setAttribute(
                "${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.OPEN_ENTRIES_IN_BROWSER}",
                feed.openEntriesInBrowser.toString(),
            )

            setAttribute(
                "${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.BLOCKED_WORDS}",
                feed.blockedWords,
            )

            setAttribute(
                "${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.SHOW_PREVIEW_IMAGES}",
                feed.showPreviewImages.toString(),
            )
        })
    }

    val result = StringWriter()

    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transform(DOMSource(document), StreamResult(result))
    }

    return result.toString()
}