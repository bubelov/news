package api

import api.standalone.StandaloneNewsApi
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class StandaloneNewsApiTests {

    @Test
    fun addFeed(): Unit = runBlocking {
        StandaloneNewsApi(
            feedQueries = mockk(),
            entryQueries = mockk(),
        )
    }
}