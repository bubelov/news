package opml

import android.util.Xml
import db.Feed
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter

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

fun readOpml(document: String): List<OpmlElement> {
    val elements = mutableListOf<OpmlElement>()

    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(StringReader(document))
    }

    var eventType = parser.eventType
    var insideOpml = false

    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            if (parser.name == Symbols.OPML) {
                insideOpml = true
            } else if (insideOpml && parser.name == Symbols.OUTLINE) {
                parser.apply {
                    elements += OpmlElement(
                        text = getAttributeValue(null, Symbols.TITLE) ?: "",
                        xmlUrl = getAttributeValue(null, Symbols.XMLURL),
                        htmlUrl = getAttributeValue(null, Symbols.HTMLURL) ?: "",
                        type = getAttributeValue(null, Symbols.TYPE),
                    )
                }
            }
        }

        eventType = parser.next()
    }

    return elements
}

fun writeOpml(feeds: List<Feed>): String {
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

    return result.toString()
}