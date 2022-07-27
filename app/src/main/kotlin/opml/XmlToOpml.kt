package opml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

fun String.toOpml(): OpmlDocument {
    val xmlDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val xmlDocument = xmlDocumentBuilder.parse(byteInputStream())
    return xmlDocument.toOpml()
}

fun Document.toOpml(): OpmlDocument {
    val opmlElement = documentElement

    if (!opmlElement.hasAttribute("version")) {
        throw Exception("OPML version is missing")
    }

    val opmlVersion = opmlVersion(opmlElement.getAttribute("version")).getOrElse {
        throw Exception("Unsupported OPML version: ${opmlElement.getAttribute("version")}", it)
    }

    val bodyElements = opmlElement.getElementsByTagName("body")

    if (bodyElements.length == 0) {
        throw Exception("Document has no body")
    }

    if (bodyElements.length > 1) {
        throw Exception("Only a single body tag is permitted")
    }

    val bodyElement = bodyElements.item(0)

    val topLevelOutlines = bodyElement
        .childNodes
        .toList()
        .filterIsInstance<Element>()
        .map { it.toOutline() }

    return OpmlDocument(
        version = opmlVersion,
        outlines = topLevelOutlines,
    )
}

private fun Element.toOutline(): OpmlOutline {
    return OpmlOutline(
        text = getAttribute("text"),
        outlines = childNodes.toList().filterIsInstance<Element>().map { it.toOutline() },
        xmlUrl = getAttribute("xmlUrl"),
        htmlUrl = getAttribute("htmlUrl"),
        extOpenEntriesInBrowser = getAttribute("news:openEntriesInBrowser").toBoolean(),
        extShowPreviewImages = when (getAttribute("news:showPreviewImages")) {
            "true" -> true
            "false" -> false
            else -> null
        },
        extBlockedWords = getAttribute("news:blockedWords"),
    )
}

private fun NodeList.toList(): List<Node> {
    val result = mutableListOf<Node>()

    for (i in 0 until length) {
        result += item(i)
    }

    return buildList {
        for (i in 0 until length) {
            add(item(i))
        }
    }
}