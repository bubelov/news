package db

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Ignore

class EntrySearchQueriesTest {

    @Test
    fun selectByQuery() {
        val db = db()
        val feed = db.insertRandomFeed()

        val entries = listOf(
            entry().copy(feedId = feed.id, contentText = "Linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "LinuX 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "Injured Irons Destroy Specifically")
        )

        entries.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(3, db.entrySearchQueries.selectByQuery("Linux").size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("LinuX").size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("linux").size)
        assertEquals(1, db.entrySearchQueries.selectByQuery("call").size)
    }

    @Test
    @Ignore("Won't work without icu, see https://github.com/requery/sqlite-android/issues/55")
    fun selectByQuery_inRussian() {
        val db = db()
        val feed = db.insertRandomFeed()

        val entries = listOf(
            entry().copy(feedId = feed.id, contentText = "Роулинг рулет гуляш"),
            entry().copy(feedId = feed.id, contentText = "РоулинГ рулет гуляш"),
            entry().copy(feedId = feed.id, contentText = "роулинг рулет гуляш"),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(3, db.entrySearchQueries.selectByQuery("Роулинг").size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("РоулинГ").size)
        assertEquals(3, db.entrySearchQueries.selectByQuery("роулинг").size)
    }
}