package db

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedQueriesTest {

    @Test
    fun insertOrReplace() {
        val db = testDb()
        val feed = db.insertRandomFeed()
        assertEquals(feed, db.feedQueries.selectAll().executeAsOne())
        db.feedQueries.insertOrReplace(feed)
        assertEquals(feed, db.feedQueries.selectAll().executeAsOne())
    }

    @Test
    fun selectAll() {
        val db = testDb()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        assertEquals(feeds.sortedBy { it.title }, db.feedQueries.selectAll().executeAsList())
    }

    @Test
    fun selectAllWithUnreadEntryCount() {
        val db = testDb()
        val feed = db.insertRandomFeed()
        val readEntries = buildList { repeat(7) { add(entry().copy(feed_id = feed.id, ext_read = true)) } }
        val unreadEntries = buildList { repeat(3) { add(entry().copy(feed_id = feed.id, ext_read = false)) } }
        (readEntries + unreadEntries).forEach { db.entryQueries.insertOrReplace(it) }
        val row = db.feedQueries.selectAllWithUnreadEntryCount().executeAsOne()
        assertEquals(3, row.unread_entries)
    }

    @Test
    fun selectById() {
        val db = testDb()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, db.feedQueries.selectById(randomFeed.id).executeAsOne())
    }

    @Test
    fun deleteAll() {
        val db = testDb()
        repeat(5) { db.insertRandomFeed() }
        db.feedQueries.deleteAll()
        assertTrue(db.feedQueries.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun deleteById() {
        val db = testDb()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        db.feedQueries.deleteById(randomFeed.id)
        assertTrue { db.feedQueries.selectById(randomFeed.id).executeAsOneOrNull() == null }
    }
}

fun Db.insertRandomFeed(): Feed = feed().apply { feedQueries.insertOrReplace(this) }

private fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    links = emptyList(),
    title = "",
    ext_open_entries_in_browser = null,
    ext_blocked_words = "",
    ext_show_preview_images = null,
)