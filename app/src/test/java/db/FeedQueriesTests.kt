package db

import co.appreactor.news.Database
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import org.junit.Before
import java.util.*

class FeedQueriesTests {

    lateinit var queries: FeedQueries

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        queries = database.feedQueries
    }
}

fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    title = "",
    selfLink = "",
    alternateLink = "",
    openEntriesInBrowser = false,
    blockedWords = "",
)