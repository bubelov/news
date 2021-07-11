package co.appreactor.feedparser

import java.io.File
import kotlin.test.Test

class Test {

    @Test
    fun `Should parse all feeds in test set`() {
        val feedDirs = listOf(
            File("src/test/resources/atom"),
            File("src/test/resources/rss"),
        )

        val feedFiles = feedDirs.map { it.listFiles()!!.toList() }.flatten()
        println("There are ${feedFiles.size} feed files")
        val feeds = feedFiles.map { feed(it.toURI().toURL()).getOrThrow() }

        feeds.forEach {
            when (it) {
                is AtomFeed -> assert(it.entries.getOrThrow().isNotEmpty())
                is RssFeed -> assert(it.channel.items.getOrThrow().isNotEmpty())
            }
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
}