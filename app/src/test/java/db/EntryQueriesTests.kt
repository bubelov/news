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
    id = "",
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