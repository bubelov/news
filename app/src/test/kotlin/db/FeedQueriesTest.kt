package db

import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FeedQueriesTest {

    @Test
    fun insertOrReplace() {
        val db = db()
        val feed = db.insertRandomFeed()
        assertEquals(feed, db.feedQueries.selectAll().single())
        db.feedQueries.insertOrReplace(feed)
        assertEquals(feed, db.feedQueries.selectAll().single())
    }

    @Test
    fun selectAll() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        assertEquals(feeds.sortedBy { it.title }, db.feedQueries.selectAll())
    }

    @Test
    fun selectAllWithUnreadEntryCount() {
        val db = db()
        val feed = db.insertRandomFeed()
        assertEquals(0, db.feedQueries.selectAllWithUnreadEntryCount().first().unreadEntries)
        val readEntries = buildList { repeat(7) { add(entry().copy(feedId = feed.id, extRead = true)) } }
        val unreadEntries = buildList { repeat(3) { add(entry().copy(feedId = feed.id, extRead = false)) } }
        (readEntries + unreadEntries).forEach { db.entryQueries.insertOrReplace(it) }
        val row = db.feedQueries.selectAllWithUnreadEntryCount().single()
        assertEquals(3, row.unreadEntries)
    }

    @Test
    fun selectById() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, db.feedQueries.selectById(randomFeed.id))
    }

    @Test
    fun deleteAll() {
        val db = db()
        repeat(5) { db.insertRandomFeed() }
        db.feedQueries.deleteAll()
        assertTrue(db.feedQueries.selectAll().isEmpty())
    }

    @Test
    fun deleteById() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        db.feedQueries.deleteById(randomFeed.id)
        assertTrue(db.feedQueries.selectById(randomFeed.id) == null)
    }
}

fun Db.insertRandomFeed(): Feed = feed().apply { feedQueries.insertOrReplace(this) }

private fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    links = emptyList(),
    title = "",
    extOpenEntriesInBrowser = null,
    extBlockedWords = "",
    extShowPreviewImages = null,
)