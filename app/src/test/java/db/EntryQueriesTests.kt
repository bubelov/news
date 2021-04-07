package db

import co.appreactor.news.Database
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class EntryQueriesTests {

    lateinit var queries: EntryQueries

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        queries = database.entryQueries
    }

    @Test
    fun selectByReadOrBookmarked() {
        val all = listOf(
            entry().copy(opened = true, bookmarked = true),
            entry().copy(opened = true, bookmarked = false),
            entry().copy(opened = false, bookmarked = false),
        )

        val unreadOrBookmarked = all.filter { !it.opened || it.bookmarked }

        queries.transaction { all.forEach { queries.insertOrReplace(it) } }

        val result = queries.selectByReadOrBookmarked(
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

        queries.apply {
            transaction { all.forEach { queries.insertOrReplace(it) } }

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

        queries.apply {
            transaction { all.forEach { queries.insertOrReplace(it) } }

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