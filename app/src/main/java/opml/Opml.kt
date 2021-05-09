package opml

import android.util.Xml
import db.Feed
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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
}

fun importOpml(xml: String): List<Outline> {
    val elements = mutableListOf<Outline>()

    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(StringReader(xml))
    }

    var eventType = parser.eventType
    var insideOpml = false

    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            if (parser.name == Symbols.OPML) {
                insideOpml = true
            } else if (insideOpml && parser.name == Symbols.OUTLINE) {
                parser.apply {
                    elements += Outline(
                        text = getAttributeValue(null, Symbols.TEXT),
                        type = getAttributeValue(null, Symbols.TYPE),
                        xmlUrl = getAttributeValue(null, Symbols.XML_URL),
                        openEntriesInBrowser = getAttributeValue(
                            null,
                            "${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.OPEN_ENTRIES_IN_BROWSER}"
                        )?.toBoolean() ?: false,
                        blockedWords = getAttributeValue(
                            null,
                            "${Symbols.NEWS_NAMESPACE_PREFIX}:${Symbols.BLOCKED_WORDS}"
                        ) ?: "",
                    )
                }
            }
        }

        eventType = parser.next()
    }

    return elements
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