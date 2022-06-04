package api

import api.standalone.StandaloneNewsApi
import db.database
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertTrue

class StandaloneNewsApiTests {

    @Test
    fun `add feed + 404`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val result = StandaloneNewsApi(database()).addFeed(server.url("/feed.atom"))
        assertTrue { result.isFailure }

        server.shutdown()
    }
}