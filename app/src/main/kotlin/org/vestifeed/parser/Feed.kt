package org.vestifeed.parser

import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

sealed class Feed

private val xmlDocumentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }

fun feed(inputStream: InputStream, mediaType: String): FeedResult {
    return if (
        mediaType.startsWith("application/rss+xml")
        || mediaType.startsWith("application/atom+xml")
        || mediaType.startsWith("application/xml")
        || mediaType.startsWith("text/xml")
    ) {
        return feedFromXml(inputStream)
    } else {
        FeedResult.UnsupportedMediaType(mediaType)
    }
}

private fun feedFromXml(inputStream: InputStream): FeedResult {
    val document = runCatching {
        xmlDocumentBuilder.parse(inputStream)
    }.getOrElse {
        return when (it) {
            is SAXException -> FeedResult.ParserError(it)
            is IOException -> FeedResult.IOError(it)
            else -> throw IllegalStateException()
        }
    }

    return when (feedType(document)) {
        FeedType.ATOM -> {
            atomFeed(document).map {
                FeedResult.Success(it)
            }.getOrElse {
                FeedResult.ParserError(it)
            }
        }

        FeedType.RSS -> {
            rssFeed(document).map {
                FeedResult.Success(it)
            }.getOrElse {
                FeedResult.ParserError(it)
            }
        }

        FeedType.UNKNOWN -> FeedResult.UnsupportedFeedType
    }
}