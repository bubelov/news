package feeds

import api.NewsApi
import db.FeedQueries
import db.feed
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.*

class FeedsRepositoryTests {

    private val api = mockk<NewsApi>(relaxUnitFun = true)

    private val db = mockk<FeedQueries>(relaxUnitFun = true)

    private val repository = FeedsRepository(
        db = db,
        api = api,
    )

    @Test
    fun insertOrReplace(): Unit = runBlocking {
        val feed = feed()
        repository.insertOrReplace(feed)
        verify { db.insertOrReplace(feed) }
        confirmVerified(db)
    }

    @Test
    fun insertByUrl(): Unit = runBlocking {
        val feed = feed()

        val feedUrl = "https://example.com/"

        coEvery { api.addFeed(feedUrl) } returns feed

        repository.insertByUrl(feedUrl)

        coVerifySequence {
            api.addFeed(feedUrl)
            db.insertOrReplace(feed)
        }

        confirmVerified(api, db)
    }

    @Test
    fun selectAll(): Unit = runBlocking {
        val feeds = listOf(feed(), feed())

        coEvery { db.selectAll() } returns mockk {
            every { executeAsList() } returns feeds
        }

        Assert.assertEquals(feeds, repository.selectAll())

        coVerify { db.selectAll() }

        confirmVerified(db)
    }

    @Test
    fun selectById(): Unit = runBlocking {
        val feed = feed()

        every { db.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        Assert.assertEquals(feed, repository.selectById(feed.id))

        verify { db.selectById(feed.id) }

        confirmVerified(db)
    }

    @Test
    fun updateTitle(): Unit = runBlocking {
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
    fun deleteById(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()

        repository.deleteById(id)

        coVerifySequence {
            api.deleteFeed(id)
            db.deleteById(id)
        }

        confirmVerified(api, db)
    }
}