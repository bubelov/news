package db

import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedQueriesTest {

    private lateinit var queries: FeedQueries

    @BeforeTest
    fun before() {
        queries = testDb().feedQueries
    }

    @Test
    fun insertOrReplace() {
        val feed = feed()
        queries.insertOrReplace(feed)
        assertEquals(feed, queries.selectAll().executeAsOne())
        queries.insertOrReplace(feed)
        assertEquals(feed, queries.selectAll().executeAsOne())
    }

    @Test
    fun selectAll() {
        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { queries.insertOrReplace(it) }
        assertEquals(feeds.sortedBy { it.title }, queries.selectAll().executeAsList())
    }

    @Test
    fun selectById() {
        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { queries.insertOrReplace(it) }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, queries.selectById(randomFeed.id).executeAsOne())
    }

    @Test
    fun deleteAll() {
        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { queries.insertOrReplace(it) }
        queries.deleteAll()
        assertTrue(queries.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun deleteById() {
        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { queries.insertOrReplace(it) }
        val randomFeed = feeds.random()
        queries.deleteById(randomFeed.id)
        assertTrue { queries.selectById(randomFeed.id).executeAsOneOrNull() == null }
    }
}

fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    title = "",
    links = emptyList(),
    openEntriesInBrowser = false,
    blockedWords = "",
    showPreviewImages = null,
)