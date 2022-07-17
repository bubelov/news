package db

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryQueriesTest {

    private lateinit var db: EntryQueries

    @BeforeTest
    fun before() {
        db = testDb().entryQueries
    }

    @Test
    fun insertOrReplace() {
        val item = entry()
        db.insertOrReplace(item)
        assertEquals(item, db.selectById(item.id).executeAsOne())
    }

    @Test
    fun selectAll() {
        val items = listOf(entry(), entry())
        items.forEach { db.insertOrReplace(it) }

        assertEquals(
            items.map { it.withoutContent() }.reversed(),
            db.selectAll().executeAsList()
        )
    }

    @Test
    fun selectById() {
        val items = listOf(
            db.insertOrReplace(),
            db.insertOrReplace(),
            db.insertOrReplace(),
        )

        assertEquals(
            items[1],
            db.selectById(items[1].id).executeAsOneOrNull(),
        )
    }

    @Test
    fun selectByFeedId() {
        val feed1 = UUID.randomUUID().toString()
        val feed2 = UUID.randomUUID().toString()

        db.insertOrReplace(entry().copy(feedId = feed1))
        db.insertOrReplace(entry().copy(feedId = feed1))
        db.insertOrReplace(entry().copy(feedId = feed2))

        assertEquals(
            1,
            db.selectByFeedId(feed2).executeAsList().size,
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val all = listOf(
            entry().copy(read = true, bookmarked = true),
            entry().copy(read = true, bookmarked = false),
            entry().copy(read = false, bookmarked = false),
        )

        all.forEach { db.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.read && !it.bookmarked }.map { it.withoutContent() },
            db.selectByReadAndBookmarked(read = false, bookmarked = false).executeAsList(),
        )
    }

    @Test
    fun selectByReadOrBookmarked() {
        val all = listOf(
            entry().copy(read = true, bookmarked = true),
            entry().copy(read = true, bookmarked = false),
            entry().copy(read = false, bookmarked = false),
        )

        all.forEach { db.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.read || it.bookmarked }.map { it.withoutContent() }.reversed(),
            db.selectByReadOrBookmarked(read = false, bookmarked = true).executeAsList(),
        )
    }

    @Test
    fun selectByRead() {
        val all = listOf(
            entry().copy(read = true),
            entry().copy(read = true),
            entry().copy(read = false),
        )

        all.forEach { db.insertOrReplace(it) }

        assertEquals(
            all.filter { it.read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByRead(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.read }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByRead(false).executeAsList(),
        )
    }

    @Test
    fun selectByReadSynced() {
        val all = listOf(
            entry().copy(readSynced = true),
            entry().copy(readSynced = false),
            entry().copy(readSynced = true),
        )

        all.forEach { db.insertOrReplace(it) }

        assertEquals(
            all.filter { it.readSynced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByReadSynced(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.readSynced }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByReadSynced(false).executeAsList(),
        )
    }

    @Test
    fun selectByBookmarked() {
        val all = listOf(
            entry().copy(bookmarked = true),
            entry().copy(bookmarked = false),
            entry().copy(bookmarked = false),
        )

        all.forEach { db.insertOrReplace(it) }

        assertEquals(
            all.filter { it.bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByBookmarked(true).executeAsList(),
        )

        assertEquals(
            all.filter { !it.bookmarked }.map { it.withoutContent() }.sortedByDescending { it.published },
            db.selectByBookmarked(false).executeAsList(),
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
            all.forEach { insertOrReplace(it) }

            updateReadByFeedId(read = true, feedId = feedId)

            selectAll().executeAsList().apply {
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
            all.forEach { insertOrReplace(it) }

            updateReadByBookmarked(read = true, bookmarked = bookmarked)

            selectAll().executeAsList().apply {
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