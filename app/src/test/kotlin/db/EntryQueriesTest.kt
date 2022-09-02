package db

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryQueriesTest {

    private lateinit var db: Db

    @BeforeTest
    fun before() {
        db = testDb()
    }

    @Test
    fun insertOrReplace() {
        val item = entry()
        db.entryQueries.insertOrReplace(item)
        assertEquals(item, db.entryQueries.selectById(item.id).executeAsOne())
    }

    @Test
    fun selectAll() {
        val items = listOf(entry(), entry())
        items.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            items.reversed(),
            db.entryQueries.selectAll().executeAsList()
        )
    }

    @Test
    fun selectByIds() {
        val items = listOf(
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
        )

        db.entryQueries.deleteById(items[1].id)

        repeat(10) { db.entryQueries.insertOrReplace() }

        assertEquals(
            listOf(items[0].id, items[2].id).sorted(),
            db.entryQueries.selectByIds(items.map { it.id }).executeAsList().sorted(),
        )
    }

    @Test
    fun selectById() {
        val items = listOf(
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
        )

        assertEquals(
            items[1],
            db.entryQueries.selectById(items[1].id).executeAsOneOrNull(),
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val feed = db.insertRandomFeed()
        
        val all = listOf(
            entry().copy(feed_id = feed.id, ext_read = true, ext_bookmarked = true),
            entry().copy(feed_id = feed.id, ext_read = true, ext_bookmarked = false),
            entry().copy(feed_id = feed.id, ext_read = false, ext_bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.ext_read && !it.ext_bookmarked }.map { it.id },
            db.entryQueries.selectByReadAndBookmarked(ext_read = listOf(false), ext_bookmarked = false).executeAsList().map { it.id },
        )
    }

    @Test
    fun selectByReadOrBookmarked() {
        val all = listOf(
            entry().copy(ext_read = true, ext_bookmarked = true),
            entry().copy(ext_read = true, ext_bookmarked = false),
            entry().copy(ext_read = false, ext_bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.ext_read || it.ext_bookmarked }.map { it.withoutContent() }.reversed(),
            db.entryQueries.selectByReadOrBookmarked(ext_read = false, ext_bookmarked = true).executeAsList(),
        )
    }

    @Test
    fun selectByRead() {
        val all = listOf(
            entry().copy(ext_read = true),
            entry().copy(ext_read = true),
            entry().copy(ext_read = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.ext_read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByRead(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.ext_read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByRead(false).executeAsList(),
        )
    }

    @Test
    fun selectByReadSynced() {
        val all = listOf(
            entry().copy(ext_read_synced = true),
            entry().copy(ext_read_synced = false),
            entry().copy(ext_read_synced = true),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.ext_read_synced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.ext_read_synced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(false).executeAsList(),
        )
    }

    @Test
    fun selectByBookmarked() {
        val all = listOf(
            entry().copy(ext_bookmarked = true),
            entry().copy(ext_bookmarked = false),
            entry().copy(ext_bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.ext_bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByBookmarked(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.ext_bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByBookmarked(false).executeAsList(),
        )
    }

    @Test
    fun updateReadByFeedId() {
        val feedId = UUID.randomUUID().toString()

        val all = listOf(
            entry().copy(feed_id = feedId, ext_read = true),
            entry().copy(ext_read = true),
            entry().copy(feed_id = feedId, ext_read = false),
            entry().copy(ext_read = false),
        )

        db.apply {
            all.forEach { entryQueries.insertOrReplace(it) }

            entryQueries.updateReadByFeedId(read = true, feedId = feedId)

            entryQueries.selectAll().executeAsList().apply {
                assertEquals(1, filter { !it.ext_read_synced }.size)
                assertEquals(2, filter { it.feed_id == feedId && it.ext_read }.size)
            }
        }
    }

    @Test
    fun updateReadByBookmarked() {
        val bookmarked = true

        val all = listOf(
            entry().copy(ext_bookmarked = true, ext_read = true),
            entry().copy(ext_read = true),
            entry().copy(ext_bookmarked = true, ext_read = false),
            entry().copy(ext_read = false),
        )

        db.apply {
            all.forEach { entryQueries.insertOrReplace(it) }

            entryQueries.updateReadByBookmarked(read = true, bookmarked = bookmarked)

            entryQueries.selectAll().executeAsList().apply {
                assertEquals(1, filterNot { it.ext_read_synced }.size)
                assertEquals(2, filter { it.ext_bookmarked && it.ext_read }.size)
            }
        }
    }
}

fun EntryQueries.insertOrReplace(): Entry {
    val entry = entry()
    insertOrReplace(entry)
    return entry
}

fun entry() = Entry(
    content_type = "",
    content_src = "",
    content_text = "",
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feed_id = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    author_name = "",
    ext_read = false,
    ext_read_synced = true,
    ext_bookmarked = false,
    ext_bookmarked_synced = true,
    ext_nc_guid_hash = "",
    ext_comments_url = "",
    ext_og_image_checked = true,
    ext_og_image_url = "",
    ext_og_image_width = 0,
    ext_og_image_height = 0,
)

fun entryWithoutContent() = EntryWithoutContent(
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feed_id = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    author_name = "",
    ext_read = false,
    ext_read_synced = true,
    ext_bookmarked = false,
    ext_bookmarked_synced = true,
    ext_nc_guid_hash = "",
    ext_comments_url = "",
    ext_og_image_checked = true,
    ext_og_image_url = "",
    ext_og_image_width = 0,
    ext_og_image_height = 0,
)

fun Entry.withoutContent() = EntryWithoutContent(
    links = links,
    summary = "",
    id = id,
    feed_id = feed_id,
    title = title,
    published = published,
    updated = updated,
    author_name = author_name,
    ext_read = ext_read,
    ext_read_synced = ext_read_synced,
    ext_bookmarked = ext_bookmarked,
    ext_bookmarked_synced = ext_bookmarked_synced,
    ext_nc_guid_hash = ext_nc_guid_hash,
    ext_comments_url = ext_comments_url,
    ext_og_image_checked = true,
    ext_og_image_url = "",
    ext_og_image_width = 0,
    ext_og_image_height = 0,
)

fun EntryWithoutContent.toEntry(): Entry {
    return Entry(
        content_type = "",
        content_src = "",
        content_text = "",
        links = links,
        summary = summary,
        id = id,
        feed_id = feed_id,
        title = title,
        published = published,
        updated = updated,
        author_name = author_name,
        ext_read = ext_read,
        ext_read_synced = ext_read_synced,
        ext_bookmarked = ext_bookmarked,
        ext_bookmarked_synced = ext_bookmarked_synced,
        ext_nc_guid_hash = ext_nc_guid_hash,
        ext_comments_url = ext_comments_url,
        ext_og_image_checked = ext_og_image_checked,
        ext_og_image_url = ext_og_image_url,
        ext_og_image_width = ext_og_image_width,
        ext_og_image_height = ext_og_image_height,
    )
}