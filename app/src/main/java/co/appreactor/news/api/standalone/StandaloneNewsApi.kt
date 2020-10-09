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
                val title = channel.getElementsByTagName("title").item(0).textContent ?: "Untitled"
                val link = channel.getElementsByTagName("link").item(0).textContent ?: ""

                Feed(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    link = uri,
                    alternateLink = link,
                    alternateLinkType = "text/html"
                )
            }

            else -> TODO()
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
                    val items = channel.getElementsByTagName("item")

                    (0 until items.length).forEach { index ->
                        val item = items.item(index) as Element

                        val title = item.getElementsByTagName("title").item(0).textContent ?: "Untitled"
                        val link = item.getElementsByTagName("link").item(0).textContent ?: ""
                        val pubDate = item.getElementsByTagName("pubDate").item(0).textContent ?: ""
                        val author = item.getElementsByTagName("author").item(0).textContent ?: ""
                        val guid = item.getElementsByTagName("guid").item(0).textContent ?: ""
                        val description = item.getElementsByTagName("description").item(0).textContent ?: ""

                        val entry = Entry(
                            id = guid,
                            feedId = feed.id,
                            title = title,
                            link = link,
                            published = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
                            updated = RSS_DATE_FORMATTER.parseDateTime(pubDate).toString(),
                            authorName = author,
                            summary = description,
                            enclosureLink = "",
                            enclosureLinkType = "",
                            viewed = false,
                            viewedSynced = true,
                            bookmarked = false,
                            bookmarkedSynced = true,
                            guidHash = "",
                        )

                        if (entryQueries.selectById(entry.id).executeAsOneOrNull() == null) {
                            entries += entry
                        }
                    }
                }
            }
        }

        return entries
    }

    override suspend fun markAsViewed(entriesIds: List<String>, viewed: Boolean) {

    }

    override suspend fun markAsBookmarked(entries: List<EntryWithoutSummary>, bookmarked: Boolean) {

    }
}