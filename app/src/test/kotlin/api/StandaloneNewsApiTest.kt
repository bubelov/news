package api

import api.standalone.StandaloneNewsApi
import db.feed
import db.testDb
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandaloneNewsApiTest {

    @Test
    fun `addFeed + 404`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val result = StandaloneNewsApi(testDb()).addFeed(server.url("/feed.atom"))
        assertTrue { result.isFailure }

        server.shutdown()
    }

    @Test
    fun getFeeds() = runBlocking {
        val db = testDb()
        val feed = feed()
        db.feedQueries.insert(feed)
        val api = StandaloneNewsApi(db)
        assertEquals(feed, api.getFeeds().single())
    }
}