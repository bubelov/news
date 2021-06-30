package co.appreactor.feedparser

import org.w3c.dom.Document

enum class FeedType {
    ATOM,
    RSS,
    UNKNOWN,
}

fun feedType(document: Document): FeedType {
    val documentElement = document.documentElement

    if (documentElement.tagName == "feed"
        && documentElement.getAttribute("xmlns") == "http://www.w3.org/2005/Atom"
    ) {
        return FeedType.ATOM
    }

    if (documentElement.tagName == "rss") {
        return FeedType.RSS
    }

    return FeedType.UNKNOWN
}