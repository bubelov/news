package co.appreactor.news.api.standalone

import co.appreactor.news.api.GetNotViewedEntriesResult
import co.appreactor.news.api.NewsApi
import co.appreactor.news.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.Instant
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.w3c.dom.Element
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class StandaloneNewsApi(
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
) : NewsApi {

    companion object {
        private val RSS_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z")
                .withZoneUTC().withLocale(Locale.US)
    }

    private val httpClient = OkHttpClient()

    override suspend fun addFeed(uri: String): Feed {
        val request = Request.Builder()
            .url(uri)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Response code: ${response.code}")
        }

        val responseBody = response.body ?: throw Exception("Response has empty body")
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(responseBody.byteStream())
        val root = document.documentElement

        return when (root.tagName) {
            "rss" -> {
                val channel = root.getElementsByTagName("channel").item(0) as Element
                val title = channel.getElementsByTagName("title").item(0).textContent
                    ?: throw Exception("RSS channel has no title")
                val link = channel.getElementsByTagName("link").item(0).textContent
                    ?: throw Exception("RSS channel has no link")

                Feed(
                    id = link,
                    title = title,
                    link = uri,
                    alternateLink = link,
                    alternateLinkType = "text/html"
                )
            }

            "atom" -> {
                if (root.getAttribute("xmlns") == "http://www.w3.org/2005/Atom") {
                    val id = root.getElementsByTagName("id").item(0).textContent
                        ?: throw Exception("Atom channel has no id")
                    val title = root.getElementsByTagName("id").item(0).textContent
                        ?: throw Exception("Atom channel has no title")

                    val links = root.getElementsByTagName("link")

                    val htmlLink = (0 until links.length).mapNotNull { index ->
                        val link = links.item(index)

                        if (link != null && link is Element && link.getAttribute("type") == "text/html") {
                            link.textContent
                        } else {
                            null
                        }
                    }.firstOrNull() ?: ""

                    Feed(
                        id = id,
                        title = title,
                        link = uri,
                        alternateLink = htmlLink,
                        alternateLinkType = "text/html"
                    )
                } else {
                    throw Exception("Unknown feed format")
                }
            }

            else -> throw Exception("Unknown feed format")
        }
    }

    override suspend fun getFeeds(): List<Feed> {
        return feedQueries.selectAll().executeAsList()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {

    }

    override suspend fun deleteFeed(feedId: String) {

    }

    override suspend fun getNotViewedEntries(): Flow<GetNotViewedEntriesResult> {
        return flowOf(GetNotViewedEntriesResult.Success(emptyList()))
    }

    override suspend fun getBookmarkedEntries(): List<Entry> {
        return emptyList()
    }

    override suspend fun getNewAndUpdatedEntries(since: Instant): List<Entry> {
        val entries = mutableListOf<Entry>()

        feedQueries.selectAll().executeAsList().forEach { feed ->
            val request = Request.Builder()
                .url(feed.link)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@forEach
            }

            val responseBody = response.body ?: return@forEach
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = builder.parse(responseBody.byteStream())
            val root = document.documentElement

            when (root.tagName) {
                "rss" -> {
                    val channel = root.getElementsByTagName("channel").item(0) as Element
                    entries += parseRssEntries(channel)
                }

                "feed" -> {
                    if (root.getAttribute("xmlns") == "http://www.w3.org/2005/Atom") {
                        entries += parseAtomEntries(root)
                    }
                }
            }
        }

        entries.removeAll {
            entryQueries.selectById(it.id).executeAsOneOrNull() != null
        }

        return entries
    }

    override suspend fun markAsViewed(entriesIds: List<String>, viewed: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }

    private fun parseRssEntries(channel: Element): List<Entry> {
        val feedId = channel.getElementsByTagName("link").item(0).textContent
            ?: return emptyList()

        val items = channel.getElementsByTagName("item")

        return (0 until items.length).mapNotNull { index ->
            val item = items.item(index) as Element

            val guid = item.getElementsByTagName("guid").item(0).textContent ?: return@mapNotNull null
            val title = item.getElementsByTagName("title").item(0).textContent ?: "Untitled"
            val link = item.getElementsByTagName("link").item(0).textContent ?: ""
            val pubDate = item.getElementsByTagName("pubDate").item(0).textContent ?: ""
            val author = item.getElementsByTagName("author").item(0).textContent ?: ""
            val description = item.getElementsByTagName("description").item(0).textContent ?: ""

            Entry(
                id = guid,
                feedId = feedId,
                title = title,
                link = link,
                published = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
                updated = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
                authorName = author,
                content = description,
                enclosureLink = "",
                enclosureLinkType = "",
                viewed = false,
                viewedSynced = true,
                bookmarked = false,
                bookmarkedSynced = true,
                guidHash = "",
            )
        }
    }

    private fun parseAtomEntries(feed: Element): List<Entry> {
        val feedId = feed.getElementsByTagName("id").item(0).textContent ?: return emptyList()
        val entries = feed.getElementsByTagName("entry")

        return (0 until entries.length).mapNotNull { index ->
            val entry = entries.item(index) as Element

            val id = entry.getElementsByTagName("id").item(0).textContent ?: return@mapNotNull null
            val title = entry.getElementsByTagName("title").item(0).textContent ?: return@mapNotNull null
            val link = entry.getElementsByTagName("link").item(0).textContent ?: return@mapNotNull null
            val published = entry.getElementsByTagName("published").item(0).textContent ?: return@mapNotNull null
            val updated = entry.getElementsByTagName("updated").item(0).textContent ?: return@mapNotNull null

            val author = entry.getElementsByTagName("author")?.item(0)

            val authorName = if (author != null && author is Element) {
                author.getElementsByTagName("name")?.item(0)?.textContent ?: ""
            } else {
                ""
            }

            val content = entry.getElementsByTagName("content").item(0).textContent ?: return@mapNotNull null

            Entry(
                id = id,
                feedId = feedId,
                title = title,
                link = link,
                published = published,
                updated = updated,
                authorName = authorName,
                content = content,
                enclosureLink = "",
                enclosureLinkType = "",
                viewed = false,
                viewedSynced = true,
                bookmarked = false,
                bookmarkedSynced = true,
                guidHash = "",
            )
        }
    }
}