package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class FeedQueriesTests {

    lateinit var db: FeedQueries

    @Before
    fun setup() {
        db = database().feedQueries
    }

    @Test
    fun insertOrReplace() {
        val feed = feed()
        db.insertOrReplace(feed)
        Assert.assertEquals(feed, db.selectAll().executeAsList().single())
    }
}

fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    title = "",
    selfLink = "",
    alternateLink = "",
    openEntriesInBrowser = false,
    blockedWords = "",
    showPreviewImages = null,
)