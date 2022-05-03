package feeds

import api.NewsApi
import db.database
import db.feed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertTrue

class FeedsRepositoryTests {

    @Test
    fun `insert or replace`(): Unit = runBlocking {
        val db = database()

        val repo = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = mockk(),
        )

        val feed = feed()
        repo.insertOrReplace(feed)

        assertEquals(
            expected = feed,
            actual = db.feedQueries.selectAll().executeAsList().single(),
        )
    }

    @Test
    fun `insert by url`(): Unit = runBlocking {
        val feedUrl = "https://example.com/".toHttpUrl()
        val feed = feed()

        val db = database()

        val api: NewsApi = mockk<NewsApi>().apply {
            coEvery { addFeed(feedUrl) } returns Result.success(feed)
        }

        val repo = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = api,
        )

        repo.insertByFeedUrl(feedUrl)

        assertEquals(
            expected = feed,
            actual = db.feedQueries.selectAll().executeAsList().single(),
        )

        coVerify { api.addFeed(feedUrl) }
        confirmVerified(api)
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
        val db = database()

        val repo = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = mockk(),
        )

        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { db.feedQueries.insertOrReplace(it) }

        val randomFeed = feeds.random()

        assertEquals(randomFeed, repo.selectById(randomFeed.id).first())
    }

    @Test
    fun `update title`(): Unit = runBlocking {
        val feed = feed()
        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        val db = database()

        val api: NewsApi = mockk<NewsApi>().apply {
            coEvery { updateFeedTitle(feed.id, trimmedNewTitle) } returns Unit
        }

        val repo = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = api,
        )

        db.feedQueries.insertOrReplace(feed)

        repo.updateTitle(
            feedId = feed.id,
            newTitle = newTitle,
        )

        assertEquals(
            expected = feed.copy(title = trimmedNewTitle),
            actual = repo.selectAll().first().single(),
        )
    }

    @Test
    fun `delete by id`(): Unit = runBlocking {
        val feeds = listOf(feed(), feed(), feed())
        val randomFeed = feeds.random()

        val db = database()

        val api: NewsApi = mockk<NewsApi>().apply {
            coEvery { deleteFeed(randomFeed.id) } returns Unit
        }

        val repo = FeedsRepository(
            feedQueries = db.feedQueries,
            entryQueries = db.entryQueries,
            api = api,
        )

        feeds.forEach { db.feedQueries.insertOrReplace(it) }

        repo.deleteById(randomFeed.id)

        assertTrue { !db.feedQueries.selectAll().executeAsList().contains(randomFeed) }
    }
}