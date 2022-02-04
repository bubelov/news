package opml

import android.util.Xml
import db.Feed
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
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
    const val NEWS_NAMESPACE = "https://appreactor.co/news"
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
    val result = StringWriter()

    Xml.newSerializer().apply {
        setOutput(result)

        startDocument("UTF-8", true)
        setPrefix(Symbols.NEWS_NAMESPACE_PREFIX, Symbols.NEWS_NAMESPACE)
        startTag(null, Symbols.OPML)
        attribute(null, Symbols.VERSION, "2.0")

        startTag(null, Symbols.HEAD)
        startTag(null, Symbols.TITLE)
        text("Subscriptions")
        endTag(null, Symbols.TITLE)
        endTag(null, Symbols.HEAD)

        startTag(null, Symbols.BODY)

        feeds.forEach { feed ->
            startTag(null, Symbols.OUTLINE)
            attribute(null, Symbols.TEXT, feed.title)
            attribute(null, Symbols.TYPE, "rss")
            attribute(null, Symbols.XML_URL, feed.selfLink)
            attribute(
                Symbols.NEWS_NAMESPACE,
                Symbols.OPEN_ENTRIES_IN_BROWSER,
                feed.openEntriesInBrowser.toString()
            )
            attribute(Symbols.NEWS_NAMESPACE, Symbols.BLOCKED_WORDS, feed.blockedWords)
            attribute(
                Symbols.NEWS_NAMESPACE,
                Symbols.SHOW_PREVIEW_IMAGES,
                feed.showPreviewImages.toString()
            )
            endTag(null, Symbols.OUTLINE)
        }

        endTag(null, Symbols.BODY)
        endTag(null, Symbols.OPML)
        endDocument()
    }

    return prettify(result.toString())
}

private fun prettify(xml: String): String {
    val result = StringWriter()

    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val document = builder.parse(InputSource(StringReader(xml)))

    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transform(DOMSource(document), StreamResult(result))
    }

    return result.toString().replaceFirst("<opml", "\n\n<opml")
}
