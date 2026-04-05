package org.vestifeed.db.table

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database
import org.vestifeed.parser.AtomLinkRel
import java.time.OffsetDateTime
import java.util.UUID

class FeedTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun feedSchema_createTableStatement() {
        val statement = FEED_SCHEMA
        assertTrue(statement.contains("CREATE TABLE feed"))
        assertTrue(statement.contains("id TEXT PRIMARY KEY NOT NULL"))
        assertTrue(statement.contains("links TEXT"))
        assertTrue(statement.contains("title TEXT NOT NULL"))
        assertTrue(statement.contains("ext_open_entries_in_browser INTEGER"))
        assertTrue(statement.contains("ext_blocked_words TEXT"))
        assertTrue(statement.contains("ext_show_preview_images INTEGER"))
    }

    @Test
    fun feedQueries_insertOrReplace() {
        val feed = createFeed()
        db.feed.insertOrReplace(feed)
        assertEquals(feed, db.feed.selectAll().single())
    }

    @Test
    fun feedQueries_insertOrReplace_multiple() {
        val feeds = listOf(createFeed(), createFeed(), createFeed())
        db.feed.insertOrReplace(feeds)
        val result = db.feed.selectAll()
        assertEquals(3, result.size)
    }

    @Test
    fun feedQueries_insertOrReplace_emptyList() {
        db.feed.insertOrReplace(emptyList())
        assertTrue(db.feed.selectAll().isEmpty())
    }

    @Test
    fun feedQueries_insertOrReplace_updatesExisting() {
        val feed = createFeed()
        db.feed.insertOrReplace(feed)

        val updated = feed.copy(title = "Updated Title")
        db.feed.insertOrReplace(updated)

        assertEquals(1, db.feed.selectAll().size)
        assertEquals("Updated Title", db.feed.selectAll().single().title)
    }

    @Test
    fun feedQueries_selectAll_sortsByTitle() {
        val feeds = listOf(
            createFeed(title = "Zebra"),
            createFeed(title = "Apple"),
            createFeed(title = "Mango"),
        )
        db.feed.insertOrReplace(feeds)

        val result = db.feed.selectAll()
        assertEquals("Apple", result[0].title)
        assertEquals("Mango", result[1].title)
        assertEquals("Zebra", result[2].title)
    }

    @Test
    fun feedQueries_selectAll_empty() {
        assertTrue(db.feed.selectAll().isEmpty())
    }

    @Test
    fun feedQueries_selectById() {
        val feeds = listOf(createFeed(), createFeed(), createFeed())
        db.feed.insertOrReplace(feeds)

        val target = feeds[1]
        assertEquals(target, db.feed.selectById(target.id))
    }

    @Test
    fun feedQueries_selectById_notFound() {
        assertNull(db.feed.selectById("non-existent-id"))
    }

    @Test
    fun feedQueries_deleteById() {
        val feeds = listOf(createFeed(), createFeed(), createFeed())
        db.feed.insertOrReplace(feeds)

        db.feed.deleteById(feeds[1].id)

        val result = db.feed.selectAll()
        assertEquals(2, result.size)
        assertTrue(result.none { it.id == feeds[1].id })
    }

    @Test
    fun feedQueries_deleteAll() {
        val feeds = listOf(createFeed(), createFeed(), createFeed())
        db.feed.insertOrReplace(feeds)

        db.feed.deleteAll()

        assertTrue(db.feed.selectAll().isEmpty())
    }

    @Test
    fun feedQueries_linksSerialization_roundTrip() {
        val link1 = Link(
            feedId = "feed1",
            entryId = "entry1",
            href = "https://example.com/self".toHttpUrl(),
            rel = AtomLinkRel.Self,
            type = "application/rss+xml",
            hreflang = "en",
            title = "Self Link",
            length = 1000L,
            extEnclosureDownloadProgress = 0.5,
            extCacheUri = "cache://uri",
        )
        val link2 = Link(
            feedId = "feed1",
            entryId = null,
            href = "https://example.com/alternate".toHttpUrl(),
            rel = AtomLinkRel.Alternate,
            type = "text/html",
            hreflang = null,
            title = "Alternate Link",
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
        val feed = createFeed(links = listOf(link1, link2))
        db.feed.insertOrReplace(feed)

        val result = db.feed.selectById(feed.id)
        assertEquals(2, result!!.links.size)

        val selfLink = result.links.find { it.rel is AtomLinkRel.Self }!!
        assertEquals("feed1", selfLink.feedId)
        assertEquals("entry1", selfLink.entryId)
        assertEquals("https://example.com/self", selfLink.href.toString())
        assertEquals("application/rss+xml", selfLink.type)
        assertEquals("en", selfLink.hreflang)
        assertEquals("Self Link", selfLink.title)
        assertEquals(1000L, selfLink.length)
        assertEquals(0.5, selfLink.extEnclosureDownloadProgress)
        assertEquals("cache://uri", selfLink.extCacheUri)

        val altLink = result.links.find { it.rel is AtomLinkRel.Alternate }!!
        assertEquals("feed1", altLink.feedId)
        assertNull(altLink.entryId)
        assertEquals("https://example.com/alternate", altLink.href.toString())
        assertEquals("text/html", altLink.type)
        assertNull(altLink.hreflang)
        assertNull(altLink.length)
        assertNull(altLink.extEnclosureDownloadProgress)
        assertNull(altLink.extCacheUri)
    }

    @Test
    fun feedQueries_nullBooleanFields_nullValues() {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "Test",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        db.feed.insertOrReplace(feed)

        val result = db.feed.selectById(feed.id)
        assertNull(result!!.extOpenEntriesInBrowser)
        assertNull(result.extShowPreviewImages)
    }

    @Test
    fun feedQueries_nullBooleanFields_trueValues() {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "Test",
            extOpenEntriesInBrowser = true,
            extBlockedWords = "",
            extShowPreviewImages = true,
        )
        db.feed.insertOrReplace(feed)

        val result = db.feed.selectById(feed.id)
        assertEquals(true, result!!.extOpenEntriesInBrowser)
        assertEquals(true, result.extShowPreviewImages)
    }

    @Test
    fun feedQueries_nullBooleanFields_falseValues() {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "Test",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = false,
        )
        db.feed.insertOrReplace(feed)

        val result = db.feed.selectById(feed.id)
        assertEquals(false, result!!.extOpenEntriesInBrowser)
        assertEquals(false, result.extShowPreviewImages)
    }

    private fun createFeed(
        id: String = UUID.randomUUID().toString(),
        links: List<Link> = emptyList(),
        title: String = "Test Feed",
        extOpenEntriesInBrowser: Boolean? = null,
        extBlockedWords: String = "",
        extShowPreviewImages: Boolean? = null,
    ) = Feed(
        id = id,
        links = links,
        title = title,
        extOpenEntriesInBrowser = extOpenEntriesInBrowser,
        extBlockedWords = extBlockedWords,
        extShowPreviewImages = extShowPreviewImages,
    )

    private fun createEntry(
        feedId: String,
        extRead: Boolean = false,
        extBookmarked: Boolean = false,
    ) = Entry(
        contentType = "",
        contentSrc = "",
        contentText = "",
        links = emptyList(),
        summary = "",
        id = UUID.randomUUID().toString(),
        feedId = feedId,
        title = "",
        published = OffsetDateTime.now(),
        updated = OffsetDateTime.now(),
        authorName = "",
        extRead = extRead,
        extReadSynced = true,
        extBookmarked = extBookmarked,
        extBookmarkedSynced = true,
        extCommentsUrl = "",
        extOpenGraphImageChecked = true,
        extOpenGraphImageUrl = "",
        extOpenGraphImageWidth = 0,
        extOpenGraphImageHeight = 0,
    )
}