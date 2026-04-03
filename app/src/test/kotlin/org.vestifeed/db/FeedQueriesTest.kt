package org.vestifeed.db

import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.vestifeed.db.table.Feed

class FeedQueriesTest {

    @Test
    fun insertOrReplace() {
        val db = db()
        val feed = db.insertRandomFeed()
        assertEquals(feed, db.feed.selectAll().single())
        db.feed.insertOrReplace(listOf(feed))
        assertEquals(feed, db.feed.selectAll().single())
    }

    @Test
    fun selectAll() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        assertEquals(feeds.sortedBy { it.title }, db.feed.selectAll())
    }

    @Test
    fun selectAllWithUnreadEntryCount() {
        val db = db()
        val feed = db.insertRandomFeed()
        assertEquals(0, db.feed.selectAllWithUnreadEntryCount().first().unreadEntries)
        val readEntries =
            buildList { repeat(7) { add(entry().copy(feedId = feed.id, extRead = true)) } }
        val unreadEntries =
            buildList { repeat(3) { add(entry().copy(feedId = feed.id, extRead = false)) } }
        (readEntries + unreadEntries).forEach { db.entry.insertOrReplace(listOf(it)) }
        val row = db.feed.selectAllWithUnreadEntryCount().single()
        assertEquals(3, row.unreadEntries)
    }

    @Test
    fun selectById() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, db.feed.selectById(randomFeed.id))
    }

    @Test
    fun deleteAll() {
        val db = db()
        repeat(5) { db.insertRandomFeed() }
        db.feed.deleteAll()
        assertTrue(db.feed.selectAll().isEmpty())
    }

    @Test
    fun deleteById() {
        val db = db()
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        db.feed.deleteById(randomFeed.id)
        assertTrue(db.feed.selectById(randomFeed.id) == null)
    }
}

fun Database.insertRandomFeed(): Feed = feed().apply { feed.insertOrReplace(listOf(this)) }

private fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    links = emptyList(),
    title = "",
    extOpenEntriesInBrowser = null,
    extBlockedWords = "",
    extShowPreviewImages = null,
)