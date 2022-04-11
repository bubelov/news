package api.miniflux

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test

class MinifluxApiAdapterTest {

    @Test
    fun `addFeed should fail if there are no categories`() {
        val api = mockk<MinifluxApi>()
        val adapter = MinifluxApiAdapter(api)
        val feedUrl = "https://test.com/feeds/atom".toHttpUrl()
        coEvery { api.getCategories() } returns emptyList()
        val result = runBlocking { adapter.addFeed(feedUrl) }
        assert(result.isFailure)
        coVerify { api.getCategories() }
    }
}