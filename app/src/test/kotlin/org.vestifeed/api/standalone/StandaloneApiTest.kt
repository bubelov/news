package org.vestifeed.api.standalone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vestifeed.db.db
import org.vestifeed.db.insertRandomFeed

class StandaloneApiTest {

    @Test
    fun addFeed404() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val result = StandaloneNewsApi(db()).addFeed(server.url("/feed.atom"))
        assertTrue(result.isFailure)

        server.shutdown()
    }

    @Test
    fun getFeeds() = runBlocking {
        val db = db()
        val feed = db.insertRandomFeed()
        val api = StandaloneNewsApi(db)
        assertEquals(feed, api.getFeeds().single())
    }
}