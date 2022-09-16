package db

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Ignore

class EntrySearchQueriesTest {

    @Test
    fun selectByQuery() {
        val db = testDb()
        val feed = db.insertRandomFeed()

        val entries = listOf(
            entry().copy(feed_id = feed.id, content_text = "Linux 5.19 introduces RSS API"),
            entry().copy(feed_id = feed.id, content_text = "LinuX 5.19 introduces RSS API"),
            entry().copy(feed_id = feed.id, content_text = "linux 5.19 introduces RSS API"),
            entry().copy(feed_id = feed.id, content_text = "Injured Irons Destroy Specifically")
        )

        entries.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(3, db.entrySearchQueries.selectByQuery("Linux").executeAsList().size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("LinuX").executeAsList().size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("linux").executeAsList().size)
        assertEquals(1, db.entrySearchQueries.selectByQuery("call").executeAsList().size)
    }

    @Test
    @Ignore("Won't work without icu, see https://github.com/requery/sqlite-android/issues/55")
    fun selectByQuery_inRussian() {
        val db = testDb()
        val feed = db.insertRandomFeed()

        val entries = listOf(
            entry().copy(feed_id = feed.id, content_text = "Роулинг рулет гуляш"),
            entry().copy(feed_id = feed.id, content_text = "РоулинГ рулет гуляш"),
            entry().copy(feed_id = feed.id, content_text = "роулинг рулет гуляш"),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(3, db.entrySearchQueries.selectByQuery("Роулинг").executeAsList().size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("РоулинГ").executeAsList().size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("роулинг").executeAsList().size)
    }
}