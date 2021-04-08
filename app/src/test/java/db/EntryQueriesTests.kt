package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class EntryQueriesTests {

    private lateinit var db: EntryQueries

    @Before
    fun setup() {
        db = database().entryQueries
    }

    @Test
    fun selectByReadOrBookmarked() {
        val all = listOf(
            entry().copy(opened = true, bookmarked = true),
            entry().copy(opened = true, bookmarked = false),
            entry().copy(opened = false, bookmarked = false),
        )

        val unreadOrBookmarked = all.filter { !it.opened || it.bookmarked }

        all.forEach { db.insertOrReplace(it) }

        val result = db.selectByReadOrBookmarked(
            read = false,
            bookmarked = true,
        ).executeAsList()

        Assert.assertEquals(
            unreadOrBookmarked.map { it.id },
            result.map { it.id },
        )
    }

    @Test
    fun updateReadByFeedId() {
        val feedId = UUID.randomUUID().toString()

        val all = listOf(
            entry().copy(feedId = feedId, opened = true),
            entry().copy(opened = true),
            entry().copy(feedId = feedId, opened = false),
            entry().copy(opened = false),
        )

        db.apply {
            all.forEach { insertOrReplace(it) }

            updateReadByFeedId(read = true, feedId = feedId)

            selectAll().executeAsList().apply {
                Assert.assertEquals(1, filterNot { it.openedSynced }.size)
                Assert.assertEquals(2, filter { it.feedId == feedId && it.opened }.size)
            }
        }
    }

    @Test
    fun updateReadByBookmarked() {
        val bookmarked = true

        val all = listOf(
            entry().copy(bookmarked = true, opened = true),
            entry().copy(opened = true),
            entry().copy(bookmarked = true, opened = false),
            entry().copy(opened = false),
        )

        db.apply {
            all.forEach { insertOrReplace(it) }

            updateReadByBookmarked(read = true, bookmarked = bookmarked)

            selectAll().executeAsList().apply {
                Assert.assertEquals(1, filterNot { it.openedSynced }.size)
                Assert.assertEquals(2, filter { it.bookmarked && it.opened }.size)
            }
        }
    }
}

fun entry() = Entry(
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    link = "",
    published = "",
    updated = "",
    authorName = "",
    content = "",
    enclosureLink = "",
    enclosureLinkType = "",
    opened = false,
    openedSynced = true,
    bookmarked = false,
    bookmarkedSynced = true,
    guidHash = "",
)

fun entryWithoutSummary() = EntryWithoutSummary(
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    link = "",
    published = "",
    updated = "",
    authorName = "",
    enclosureLink = "",
    enclosureLinkType = "",
    opened = false,
    openedSynced = true,
    bookmarked = false,
    bookmarkedSynced = true,
    guidHash = "",
)