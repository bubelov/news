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
            items.map { it.withoutContent() }.reversed(),
            db.entryQueries.selectAll().executeAsList()
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
    fun selectByFeedId() {
        val feed1 = UUID.randomUUID().toString()
        val feed2 = UUID.randomUUID().toString()

        db.entryQueries.insertOrReplace(entry().copy(feedId = feed1))
        db.entryQueries.insertOrReplace(entry().copy(feedId = feed1))
        db.entryQueries.insertOrReplace(entry().copy(feedId = feed2))

        assertEquals(
            1,
            db.entryQueries.selectByFeedId(feed2).executeAsList().size,
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val feed = feed()
        db.feedQueries.insertOrReplace(feed)
        
        val all = listOf(
            entry().copy(feedId = feed.id, read = true, bookmarked = true),
            entry().copy(feedId = feed.id, read = true, bookmarked = false),
            entry().copy(feedId = feed.id, read = false, bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.read && !it.bookmarked }.map { it.id },
            db.entryQueries.selectByReadAndBookmarked(read = listOf(false), bookmarked = false).executeAsList().map { it.id },
        )
    }

    @Test
    fun selectByReadOrBookmarked() {
        val all = listOf(
            entry().copy(read = true, bookmarked = true),
            entry().copy(read = true, bookmarked = false),
            entry().copy(read = false, bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.read || it.bookmarked }.map { it.withoutContent() }.reversed(),
            db.entryQueries.selectByReadOrBookmarked(read = false, bookmarked = true).executeAsList(),
        )
    }

    @Test
    fun selectByRead() {
        val all = listOf(
            entry().copy(read = true),
            entry().copy(read = true),
            entry().copy(read = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByRead(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByRead(false).executeAsList(),
        )
    }

    @Test
    fun selectByReadSynced() {
        val all = listOf(
            entry().copy(readSynced = true),
            entry().copy(readSynced = false),
            entry().copy(readSynced = true),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.readSynced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.readSynced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(false).executeAsList(),
        )
    }

    @Test
    fun selectByBookmarked() {
        val all = listOf(
            entry().copy(bookmarked = true),
            entry().copy(bookmarked = false),
            entry().copy(bookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByBookmarked(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.entryQueries.selectByBookmarked(false).executeAsList(),
        )
    }

    @Test
    fun updateReadByFeedId() {
        val feedId = UUID.randomUUID().toString()

        val all = listOf(
            entry().copy(feedId = feedId, read = true),
            entry().copy(read = true),
            entry().copy(feedId = feedId, read = false),
            entry().copy(read = false),
        )

        db.apply {
            all.forEach { entryQueries.insertOrReplace(it) }

            entryQueries.updateReadByFeedId(read = true, feedId = feedId)

            entryQueries.selectAll().executeAsList().apply {
                assertEquals(1, filter { !it.readSynced }.size)
                assertEquals(2, filter { it.feedId == feedId && it.read }.size)
            }
        }
    }

    @Test
    fun updateReadByBookmarked() {
        val bookmarked = true

        val all = listOf(
            entry().copy(bookmarked = true, read = true),
            entry().copy(read = true),
            entry().copy(bookmarked = true, read = false),
            entry().copy(read = false),
        )

        db.apply {
            all.forEach { entryQueries.insertOrReplace(it) }

            entryQueries.updateReadByBookmarked(read = true, bookmarked = bookmarked)

            entryQueries.selectAll().executeAsList().apply {
                assertEquals(1, filterNot { it.readSynced }.size)
                assertEquals(2, filter { it.bookmarked && it.read }.size)
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
    contentType = "",
    contentSrc = "",
    contentText = "",
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    read = false,
    readSynced = true,
    bookmarked = false,
    bookmarkedSynced = true,
    guidHash = "",
    commentsUrl = "",
    ogImageChecked = true,
    ogImageUrl = "",
    ogImageWidth = 0,
    ogImageHeight = 0,
)

fun entryWithoutContent() = EntryWithoutContent(
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    read = false,
    readSynced = true,
    bookmarked = false,
    bookmarkedSynced = true,
    guidHash = "",
    commentsUrl = "",
    ogImageChecked = true,
    ogImageUrl = "",
    ogImageWidth = 0,
    ogImageHeight = 0,
)

fun Entry.withoutContent() = EntryWithoutContent(
    links = links,
    summary = "",
    id = id,
    feedId = feedId,
    title = title,
    published = published,
    updated = updated,
    authorName = authorName,
    read = read,
    readSynced = readSynced,
    bookmarked = bookmarked,
    bookmarkedSynced = bookmarkedSynced,
    guidHash = guidHash,
    commentsUrl = commentsUrl,
    ogImageChecked = true,
    ogImageUrl = "",
    ogImageWidth = 0,
    ogImageHeight = 0,
)

fun EntryWithoutContent.toEntry(): Entry {
    return Entry(
        contentType = "",
        contentSrc = "",
        contentText = "",
        links = links,
        summary = summary,
        id = id,
        feedId = feedId,
        title = title,
        published = published,
        updated = updated,
        authorName = authorName,
        read = read,
        readSynced = readSynced,
        bookmarked = bookmarked,
        bookmarkedSynced = bookmarkedSynced,
        guidHash = guidHash,
        commentsUrl = commentsUrl,
        ogImageChecked = ogImageChecked,
        ogImageUrl = ogImageUrl,
        ogImageWidth = ogImageWidth,
        ogImageHeight = ogImageHeight,
    )
}