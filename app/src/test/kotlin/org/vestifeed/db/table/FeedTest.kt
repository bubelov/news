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
    fun feedSchema_tableName() {
        assertEquals("feed", FeedSchema.TABLE_NAME)
    }

    @Test
    fun feedSchema_columns() {
        val columns = FeedSchema.Columns.entries
        assertEquals(6, columns.size)
        assertEquals("id", columns[0].sqlName)
        assertEquals("links", columns[1].sqlName)
        assertEquals("title", columns[2].sqlName)
        assertEquals("ext_open_entries_in_browser", columns[3].sqlName)
        assertEquals("ext_blocked_words", columns[4].sqlName)
        assertEquals("ext_show_preview_images", columns[5].sqlName)
    }

    @Test
    fun feedSchema_createTableStatement() {
        val statement = FeedSchema.toString()
        assertTrue(statement.contains("CREATE TABLE feed"))
        assertTrue(statement.contains("id TEXT PRIMARY KEY NOT NULL"))
        assertTrue(statement.contains("links TEXT"))
        assertTrue(statement.contains("title TEXT NOT NULL"))
        assertTrue(statement.contains("ext_open_entries_in_browser INTEGER"))
        assertTrue(statement.contains("ext_blocked_words TEXT"))
        assertTrue(statement.contains("ext_show_preview_images INTEGER"))
    }

    @Test
    fun feedProjection_columns() {
        val columns = FeedProjection.columns
        assertEquals("id,links,title,ext_open_entries_in_browser,ext_blocked_words,ext_show_preview_images", columns)
    }

    @Test
    fun feedQueries_insertOrReplace() {
        val feed = createFeed()
        db.feed.insertOrReplace(listOf(feed))
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
        db.feed.insertOrReplace(listOf(feed))

        val updated = feed.copy(title = "Updated Title")
        db.feed.insertOrReplace(listOf(updated))

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
    fun feedQueries_selectAllWithUnreadEntryCount_noEntries() {
        val feed = createFeed()
        db.feed.insertOrReplace(listOf(feed))

        val result = db.feed.selectAllWithUnreadEntryCount()
        assertEquals(1, result.size)
        assertEquals(0, result[0].unreadEntries)
    }

    @Test
    fun feedQueries_selectAllWithUnreadEntryCount_withUnreadEntries() {
        val feed = createFeed()
        db.feed.insertOrReplace(listOf(feed))

        val readEntry = createEntry(feed.id, extRead = true, extBookmarked = false)
        val unreadEntry = createEntry(feed.id, extRead = false, extBookmarked = false)
        val bookmarkedEntry = createEntry(feed.id, extRead = false, extBookmarked = true)
        db.entry.insertOrReplace(listOf(readEntry, unreadEntry, bookmarkedEntry))

        val result = db.feed.selectAllWithUnreadEntryCount()
        assertEquals(1, result.size)
        assertEquals(1, result[0].unreadEntries)
    }

    @Test
    fun feedQueries_selectAllWithUnreadEntryCount_multipleFeeds() {
        val feed1Id = UUID.randomUUID().toString()
        val feed2Id = UUID.randomUUID().toString()
        
        val feed1 = Feed(
            id = feed1Id,
            links = emptyList(),
            title = "Feed1",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        val feed2 = Feed(
            id = feed2Id,
            links = emptyList(),
            title = "Feed2",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        
        db.feed.insertOrReplace(listOf(feed1, feed2))
        
        val entry1 = createEntry(feed1Id, extRead = false, extBookmarked = false)
        val entry2 = createEntry(feed1Id, extRead = false, extBookmarked = false)
        val entry3 = createEntry(feed2Id, extRead = false, extBookmarked = false)
        
        db.entry.insertOrReplace(listOf(entry1, entry2, entry3))

        val result = db.feed.selectAllWithUnreadEntryCount()
        
        assertEquals(2, result.size)
        
        val feed1Result = result.find { it.title == "Feed1" }!!
        val feed2Result = result.find { it.title == "Feed2" }!!
        
        assertEquals(2, feed1Result.unreadEntries)
        assertEquals(1, feed2Result.unreadEntries)
    }

    @Test
    fun feedQueries_selectAllLinks_emptyLinks() {
        val feed = createFeed(links = emptyList())
        db.feed.insertOrReplace(listOf(feed))

        val result = db.feed.selectAllLinks()
        assertEquals(1, result.size)
        assertTrue(result[0].isEmpty())
    }

    @Test
    fun feedQueries_selectAllLinks_withLinks() {
        val link = Link(
            feedId = "feed1",
            entryId = null,
            href = "https://example.com/feed".toHttpUrl(),
            rel = AtomLinkRel.Self,
            type = "application/rss+xml",
            hreflang = "en",
            title = "Example Feed",
            length = 1234L,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
        val feed = createFeed(links = listOf(link))
        db.feed.insertOrReplace(listOf(feed))

        val result = db.feed.selectAllLinks()
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals("https://example.com/feed", result[0][0].href.toString())
        assertEquals(AtomLinkRel.Self, result[0][0].rel)
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
        db.feed.insertOrReplace(listOf(feed))

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
        db.feed.insertOrReplace(listOf(feed))

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
        db.feed.insertOrReplace(listOf(feed))

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
        db.feed.insertOrReplace(listOf(feed))

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