package db

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FeedQueriesTest {

    lateinit var db: FeedQueries

    @Before
    fun setup() {
        db = database().feedQueries
    }

    @Test
    fun `insert or replace`() {
        val feed = db.insertOrReplace()
        Assert.assertEquals(feed, db.selectAll().executeAsList().single())
        db.insertOrReplace(feed)
        Assert.assertEquals(feed, db.selectAll().executeAsList().single())
    }

    @Test
    fun `select all`() {
        val rows = listOf(db.insertOrReplace(), db.insertOrReplace(), db.insertOrReplace())
        Assert.assertEquals(rows.sortedBy { it.title }, db.selectAll().executeAsList())
    }

    @Test
    fun `select by id`() {
        val row = db.insertOrReplace()
        Assert.assertEquals(row, db.selectById(row.id).executeAsOne())
    }

    @Test
    fun `delete all`() {
        repeat(3) { db.insertOrReplace() }
        db.deleteAll()
        Assert.assertTrue(db.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun `delete by id`() {
        val rows = mutableListOf(db.insertOrReplace(), db.insertOrReplace(), db.insertOrReplace())
        db.deleteById(rows.removeAt(1).id)
        Assert.assertEquals(rows.sortedBy { it.title }, db.selectAll().executeAsList())
    }
}

fun FeedQueries.insertOrReplace(): Feed {
    val feed = feed()
    insertOrReplace(feed)
    return feed
}

fun feed() = Feed(
    id = UUID.randomUUID().toString(),
    title = UUID.randomUUID().toString(),
    selfLink = "",
    alternateLink = "",
    openEntriesInBrowser = false,
    blockedWords = "",
    showPreviewImages = null,
)