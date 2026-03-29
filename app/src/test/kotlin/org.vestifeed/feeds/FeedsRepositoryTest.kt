package org.vestifeed.feeds

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.vestifeed.api.Api
import org.vestifeed.db.Entry
import org.vestifeed.db.Feed
import org.vestifeed.db.db
import org.vestifeed.db.insertRandomFeed

class FeedsRepositoryTest {

    @Test
    fun insertOrReplace() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)

        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )

        repo.insertOrReplace(feed)

        assertEquals(feed, db.feed.selectAll().single())
    }

    @Test
    fun insertByUrl() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)
        val feedUrl = "https://example.com/".toHttpUrl()

        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )

        val entries = listOf<Entry>()

        val res = Pair(feed, entries)

        coEvery { api.addFeed(feedUrl) } returns Result.success(res)

        repo.insertByUrl(feedUrl)

        assertEquals(feed, db.feed.selectAll().single())

        coVerify { api.addFeed(feedUrl) }
        confirmVerified(api)
    }

    @Test
    fun selectAll() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        repo.refresh()
        assertEquals(feeds.sortedBy { it.title }, repo.selectAll().first())
    }

    @Test
    fun selectById() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()
        assertEquals(randomFeed, repo.selectById(randomFeed.id).first())
    }

    @Test
    fun updateTitle() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)

        val feed = Feed(
            id = UUID.randomUUID().toString(),
            links = emptyList(),
            title = "",
            extOpenEntriesInBrowser = null,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )

        val newTitle = "  ${feed.title}_modified "
        val trimmedNewTitle = newTitle.trim()

        coEvery { api.updateFeedTitle(feed.id, trimmedNewTitle) } returns Result.success(Unit)

        db.feed.insertOrReplace(feed)

        repo.updateTitle(
            feedId = feed.id,
            newTitle = newTitle,
        )

        assertEquals(feed.copy(title = trimmedNewTitle), repo.selectAll().first().single())
    }

    @Test
    fun deleteById() = runBlocking {
        val db = db()
        val api: Api = mockk()
        val repo = FeedsRepo(api, db)
        val feeds = buildList { repeat(5) { add(db.insertRandomFeed()) } }
        val randomFeed = feeds.random()

        coEvery { api.deleteFeed(randomFeed.id) } returns Result.success(Unit)

        feeds.forEach { db.feed.insertOrReplace(it) }

        repo.deleteById(randomFeed.id)

        assertTrue(db.feed.selectById(randomFeed.id) == null)
    }
}