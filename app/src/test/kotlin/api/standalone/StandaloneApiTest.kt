package api.standalone

import db.insertRandomFeed
import db.testDb
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneApiTest {

    @Test
    fun addFeed404() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val result = StandaloneNewsApi(testDb()).addFeed(server.url("/feed.atom"))
        assertTrue(result.isFailure)

        server.shutdown()
    }

    @Test
    fun getFeeds() = runBlocking {
        val db = testDb()
        val feed = db.insertRandomFeed()
        val api = StandaloneNewsApi(db)
        assertEquals(feed, api.getFeeds().getOrThrow().single())
    }
}