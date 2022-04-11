package feeds

import api.NewsApi
import db.FeedQueries
import db.feed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FeedsRepositoryTests {

    private lateinit var db: FeedQueries
    private lateinit var api: NewsApi

    private lateinit var repository: FeedsRepository

    @Before
    fun setup() {
        db = mockk(relaxUnitFun = true)
        api = mockk(relaxUnitFun = true)
        repository = FeedsRepository(api, db)
    }

    @Test
    fun `insert or replace`(): Unit = runBlocking {
        val feed = feed()
        repository.insertOrReplace(feed)
        verify { db.insertOrReplace(feed) }
        confirmVerified(db)
    }

    @Test
    fun `insert by url`(): Unit = runBlocking {
        val feed = feed()

        val feedUrl = "https://example.com/".toHttpUrl()

        coEvery { api.addFeed(feedUrl) } returns Result.success(feed)

        repository.insertByFeedUrl(feedUrl)

        coVerifySequence {
            api.addFeed(feedUrl)
            db.insertOrReplace(feed)
        }

        confirmVerified(api, db)
    }

    @Test
    fun `select all`(): Unit = runBlocking {
        val feeds = listOf(feed(), feed())

        coEvery { db.selectAll() } returns mockk {
            every { executeAsList() } returns feeds
        }

        assertEquals(feeds, repository.selectAll())

        coVerify { db.selectAll() }

        confirmVerified(db)
    }

    @Test
    fun `select by id`(): Unit = runBlocking {
        val feed = feed()

        every { db.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        assertEquals(feed, repository.selectById(feed.id))

        verify { db.selectById(feed.id) }

        confirmVerified(db)
    }

    @Test
    fun `update title`(): Unit = runBlocking {
        val feed = feed()

        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        every { db.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        repository.updateTitle(
            feedId = feed.id,
            newTitle = newTitle,
        )

        coVerifySequence {
            db.selectById(feed.id)
            api.updateFeedTitle(feed.id, trimmedNewTitle)
            db.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }

        confirmVerified(api, db)
    }

    @Test
    fun `delete by id`(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()

        repository.deleteById(id)

        coVerifySequence {
            api.deleteFeed(id)
            db.deleteById(id)
        }

        confirmVerified(api, db)
    }
}