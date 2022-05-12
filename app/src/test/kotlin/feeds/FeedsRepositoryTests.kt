package feeds

import api.NewsApi
import db.Database
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeedsRepositoryTests {

    private lateinit var db: Database
    private lateinit var api: NewsApi
    private lateinit var repo: FeedsRepository

    @BeforeTest
    fun setup() {
        db = database()
        api = mockk()

        repo = FeedsRepository(
            db = db,
            api = api,
        )
    }

    @Test
    fun insertOrReplace() = runBlocking {
        val feed = feed()
        repo.insertOrReplace(feed)

        assertEquals(
            expected = feed,
            actual = db.feedQueries.selectAll().executeAsList().single(),
        )
    }

    @Test
    fun insertByUrl() = runBlocking {
        val feedUrl = "https://example.com/".toHttpUrl()
        val feed = feed()

        coEvery { api.addFeed(feedUrl) } returns Result.success(Pair(feed, emptyList()))

        repo.insertByUrl(feedUrl)

        assertEquals(
            expected = feed,
            actual = db.feedQueries.selectAll().executeAsList().single(),
        )

        coVerify { api.addFeed(feedUrl) }
        confirmVerified(api)
    }

    @Test
    fun selectAll() = runBlocking {
        val feeds = listOf(feed(), feed())
        feeds.forEach { db.feedQueries.insertOrReplace(it) }
        assertEquals(feeds.sortedBy { it.title }, repo.selectAll().first())
    }

    @Test
    fun selectById() = runBlocking {
        val feeds = listOf(feed(), feed(), feed())
        feeds.forEach { db.feedQueries.insertOrReplace(it) }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, repo.selectById(randomFeed.id).first())
    }

    @Test
    fun updateTitle() = runBlocking {
        val feed = feed()
        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        coEvery { api.updateFeedTitle(feed.id, trimmedNewTitle) } returns Unit

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
    fun deleteById() = runBlocking {
        val feeds = listOf(feed(), feed(), feed())
        val randomFeed = feeds.random()

        coEvery { api.deleteFeed(randomFeed.id) } returns Unit

        feeds.forEach { db.feedQueries.insertOrReplace(it) }

        repo.deleteById(randomFeed.id)

        assertTrue { db.feedQueries.selectById(randomFeed.id).executeAsOneOrNull() == null }
    }
}