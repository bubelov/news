package api.miniflux

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import kotlin.random.Random

class MinifluxApiAdapterTest {

    @Test
    fun addFeedFailsWithoutCategories() = runBlocking {
        val api = mockk<MinifluxApi>()
        val adapter = MinifluxApiAdapter(api)
        val feedUrl = "https://test.com/feeds/atom".toHttpUrl()
        coEvery { api.getCategories() } returns emptyList()
        val result = adapter.addFeed(feedUrl)
        assert(result.isFailure)
        coVerify { api.getCategories() }
    }

    @Test
    fun addFeed() = runBlocking {
        val siteUrl = "https://test.com"
        val feedUrl = "$siteUrl/feeds/atom"

        val categoryJson = CategoryJson(
            id = 0L,
            title = "",
            user_id = 0L,
            hide_globally = false,
        )

        val feedJson = FeedJson(
            id = Random.Default.nextLong(),
            title = "",
            feed_url = feedUrl,
            site_url = siteUrl,
        )

        val api = mockk<MinifluxApi>()
        coEvery { api.getCategories() } returns listOf(categoryJson)
        coEvery { api.postFeed(any()) } returns PostFeedResponse(feedJson.id!!)
        coEvery { api.getFeed(feedJson.id!!) } returns feedJson

        val adapter = MinifluxApiAdapter(api)
        val result = adapter.addFeed(feedUrl.toHttpUrl())
        assert(result.isSuccess)

        coVerifySequence {
            api.getCategories()
            api.postFeed(any())
            api.getFeed(feedJson.id!!)
        }
    }

    @Test
    fun getFeeds() = runBlocking {
        val feedJson = FeedJson(
            id = Random.Default.nextLong(),
            title = "",
            feed_url = "https://acme.org",
            site_url = "https://acme.org",
        )

        val api = mockk<MinifluxApi>()
        coEvery { api.getFeeds() } returns listOf(feedJson)

        val adapter = MinifluxApiAdapter(api)
        val result = adapter.getFeeds()
        assert(result.isSuccess)

        coVerify { api.getFeeds() }
    }
}