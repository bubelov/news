package co.appreactor.feedparser

import org.junit.Test
import java.io.File

class Tests {

    @Test
    fun `Should parse all feeds in test set`() {
        println("ATOM feeds: ${atomFeeds().size}")
        println("RSS feeds: ${rssFeeds().size}")

        atomFeeds().forEach {
            assert(it.entries.getOrThrow().isNotEmpty())
        }

        rssFeeds().forEach {
            assert(it.channel.items.getOrThrow().isNotEmpty())
        }
    }

    @Test
    fun `Should parse RFC 822 dates`() {
        val dates = listOf(
            "Mon, 21 Jan 2019 16:06:12 GMT",
            "Mon, 27 Jan 2020 17:55:00 EST",
            "Sat, 13 Mar 2021 08:47:51 -0500",
        )

        dates.forEach { date ->
            println("Testing date: $date")
            val parsedDate = RFC_822.parse(date).toInstant()
            println("Parsed date: $parsedDate")
        }
    }

    private fun atomFeeds() = File("src/test/resources/atom")
        .listFiles()!!
        .map { feed(it.toURI().toURL()).getOrThrow() }
        .map { if (it is AtomFeed) it else throw Exception() }

    private fun rssFeeds() = File("src/test/resources/rss")
        .listFiles()!!
        .map { feed(it.toURI().toURL()).getOrThrow() }
        .map { if (it is RssFeed) it else throw Exception() }
}