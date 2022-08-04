package opml

import org.w3c.dom.Document
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun OpmlDocument.toXmlDocument(): Document {
    val builderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val document = builderFactory.newDocumentBuilder().newDocument()

    val opmlElement = document.createElement("opml")
    opmlElement.setAttribute("xmlns:news", "https://appreactor.co/news")
    opmlElement.setAttribute("version", version.value)

    document.appendChild(opmlElement)

    val headElement = document.createElement("head")
    opmlElement.appendChild(headElement)

    val titleElement = document.createElement("title")
    titleElement.textContent = "Subscriptions"

    headElement.appendChild(titleElement)

    val bodyElement = document.createElement("body")
    opmlElement.appendChild(bodyElement)

    leafOutlines().forEach { outline ->
        bodyElement.appendChild(document.createElement("outline").apply {
            setAttribute("text", outline.text)
            setAttribute("xmlUrl", outline.xmlUrl)
            setAttribute("htmlUrl", outline.htmlUrl)
            setAttribute("news:openEntriesInBrowser", outline.extOpenEntriesInBrowser.toString())
            setAttribute("news:blockedWords", outline.extBlockedWords)
            setAttribute("news:showPreviewImages", outline.extShowPreviewImages.toString())
        })
    }

    return document
}

fun Document.toPrettyString(): String {
    val result = StringWriter()

    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transform(DOMSource(this@toPrettyString), StreamResult(result))
    }

    return result.toString()
}