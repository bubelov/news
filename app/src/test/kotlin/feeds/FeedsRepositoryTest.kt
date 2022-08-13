package feeds

import api.Api
import db.Db
import db.Feed
import db.insertRandomFeed
import db.testDb
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedsRepositoryTest {

    private lateinit var db: Db
    private lateinit var api: Api
    private lateinit var repo: FeedsRepo

    @BeforeTest
    fun before() {
        db = testDb()
        api = mockk()

        repo = FeedsRepo(
            db = db,
            api = api,
        )
    }

    @Test
    fun insertOrReplace() = runBlocking {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            ext_open_entries_in_browser = null,
            ext_blocked_words = "",
            ext_show_preview_images = null,
        )

        repo.insertOrReplace(feed)

        assertEquals(
            expected = feed,
            actual = db.feedQueries.selectAll().executeAsList().single(),
        )
    }

    @Test
    fun insertByUrl() = runBlocking {
        val feedUrl = "https://example.com/".toHttpUrl()

        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            ext_open_entries_in_browser = null,
            ext_blocked_words = "",
            ext_show_preview_images = null,
        )

        coEvery { api.addFeed(feedUrl) } returns Result.success(feed)

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
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        assertEquals(feeds.sortedBy { it.title }, repo.selectAll().first())
    }

    @Test
    fun selectById() = runBlocking {
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, repo.selectById(randomFeed.id).first())
    }

    @Test
    fun updateTitle() = runBlocking {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            ext_open_entries_in_browser = null,
            ext_blocked_words = "",
            ext_show_preview_images = null,
        )

        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        coEvery { api.updateFeedTitle(feed.id, trimmedNewTitle) } returns Result.success(Unit)

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
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()

        coEvery { api.deleteFeed(randomFeed.id) } returns Result.success(Unit)

        feeds.forEach { db.feedQueries.insertOrReplace(it) }

        repo.deleteById(randomFeed.id)

        assertTrue { db.feedQueries.selectById(randomFeed.id).executeAsOneOrNull() == null }
    }
}