package feeds

import api.NewsApi
import db.EntryQueries
import db.FeedQueries
import db.database
import db.feed
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FeedsRepositoryTests {

    private lateinit var feedQueries: FeedQueries
    private lateinit var entryQueries: EntryQueries
    private lateinit var api: NewsApi

    private lateinit var repository: FeedsRepository

    @Before
    fun setup() {
        feedQueries = mockk(relaxUnitFun = true)
        entryQueries = mockk(relaxUnitFun = true)
        api = mockk(relaxUnitFun = true)
        repository = FeedsRepository(feedQueries, entryQueries, api)
    }

    @Test
    fun `insert or replace`(): Unit = runBlocking {
        val feed = feed()
        repository.insertOrReplace(feed)
        verify { feedQueries.insertOrReplace(feed) }
        confirmVerified(feedQueries)
    }

    @Test
    fun `insert by url`(): Unit = runBlocking {
        val feed = feed()

        val feedUrl = "https://example.com/".toHttpUrl()

        coEvery { api.addFeed(feedUrl) } returns Result.success(feed)

        repository.insertByFeedUrl(feedUrl)

        coVerifySequence {
            api.addFeed(feedUrl)
            feedQueries.insertOrReplace(feed)
        }

        confirmVerified(api, feedQueries)
    }

    @Test
    fun `select all`(): Unit = runBlocking {
        val db = database()

        val repository = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = mockk(),
        )

        val feeds = listOf(feed(), feed())
        feeds.forEach { db.feedQueries.insertOrReplace(it) }
        assertEquals(feeds.sortedBy { it.title }, repository.selectAll().first())
    }

    @Test
    fun `select by id`(): Unit = runBlocking {
        val feed = feed()

        every { feedQueries.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        assertEquals(feed, repository.selectById(feed.id))

        verify { feedQueries.selectById(feed.id) }

        confirmVerified(feedQueries)
    }

    @Test
    fun `update title`(): Unit = runBlocking {
        val feed = feed()

        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        every { feedQueries.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        repository.updateTitle(
            feedId = feed.id,
            newTitle = newTitle,
        )

        coVerifySequence {
            feedQueries.selectById(feed.id)
            api.updateFeedTitle(feed.id, trimmedNewTitle)
            feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }

        confirmVerified(api, feedQueries)
    }

    @Test
    fun `delete by id`(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()

        repository.deleteById(id)

        coVerifySequence {
            api.deleteFeed(id)
            feedQueries.transaction(false, any())
        }

        confirmVerified(api, feedQueries)
    }
}