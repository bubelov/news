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
    const val BODY = "body"
    const val OUTLINE = "outline"
    const val TEXT = "text"
    const val XMLURL = "xmlUrl"
    const val HTMLURL = "htmlUrl"
    const val TYPE = "type"
    const val VERSION = "version"
    const val HEAD = "head"
    const val TITLE = "title"
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
                        xmlUrl = getAttributeValue(null, Symbols.XMLURL),
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

        startDocument("UTF-8", false)
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
            attribute(null, Symbols.TITLE, feed.title)
            attribute(null, Symbols.TYPE, "rss")
            attribute(null, Symbols.XMLURL, feed.selfLink)
            attribute(null, Symbols.HTMLURL, feed.alternateLink)
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