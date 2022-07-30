package entries

import db.entry
import db.entryWithoutContent
import db.feed
import db.testDb
import db.toEntry
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EntriesRepositoryTest {

    @Test
    fun selectAll(): Unit = runBlocking {
        val db = testDb()

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val entries = listOf(entryWithoutContent())
        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(entries, repo.selectAll().first())
    }

    @Test
    fun selectById(): Unit = runBlocking {
        val db = testDb()

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val entries = listOf(entry(), entry(), entry())
        entries.forEach { db.entryQueries.insertOrReplace(it) }

        val randomEntry = entries.random()

        assertEquals(randomEntry, repo.selectById(randomEntry.id).first())
    }

    @Test
    fun selectByFeedId(): Unit = runBlocking {
        val db = testDb()

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val feedId = UUID.randomUUID().toString()

        val entries = listOf(
            entryWithoutContent().copy(feedId = UUID.randomUUID().toString()),
            entryWithoutContent().copy(feedId = feedId),
            entryWithoutContent().copy(feedId = feedId),
            entryWithoutContent().copy(feedId = UUID.randomUUID().toString()),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(entries.filter { it.feedId == feedId }, repo.selectByFeedId(feedId).first())
    }

    @Test
    fun selectByReadAndBookmarked(): Unit = runBlocking {
        val db = testDb()

        val feed = feed()
        db.feedQueries.insertOrReplace(feed)

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val entries = listOf(
            entryWithoutContent().copy(feedId = feed.id, read = true, bookmarked = true),
            entryWithoutContent().copy(feedId = feed.id, read = true, bookmarked = false),
            entryWithoutContent().copy(feedId = feed.id, read = false, bookmarked = true),
            entryWithoutContent().copy(feedId = feed.id, read = false, bookmarked = false),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.read && it.bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = true).first().map { it.id },
        )

        assertEquals(
            entries.filter { it.read && !it.bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = false).first().map { it.id },
        )

        assertEquals(
            entries.filter { !it.read && it.bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = true).first().map { it.id },
        )

        assertEquals(
            entries.filter { !it.read && !it.bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = false).first().map { it.id },
        )
    }

    @Test
    fun selectByReadOrBookmarked(): Unit = runBlocking {
        val db = testDb()

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val entries = listOf(
            entryWithoutContent().copy(read = true, bookmarked = true),
            entryWithoutContent().copy(read = true, bookmarked = false),
            entryWithoutContent().copy(read = false, bookmarked = true),
            entryWithoutContent().copy(read = false, bookmarked = false),
        ).sortedByDescending { it.published }

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.read || it.bookmarked },
            repo.selectByReadOrBookmarked(read = true, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { it.read || !it.bookmarked },
            repo.selectByReadOrBookmarked(read = true, bookmarked = false).first(),
        )

        assertEquals(
            entries.filter { !it.read || it.bookmarked },
            repo.selectByReadOrBookmarked(read = false, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { !it.read || !it.bookmarked },
            repo.selectByReadOrBookmarked(read = false, bookmarked = false).first(),
        )
    }

    @Test
    fun selectByRead(): Unit = runBlocking {
        val db = testDb()

        val repo = EntriesRepo(
            db = db,
            api = mockk(),
        )

        val entries = listOf(
            entryWithoutContent().copy(read = true),
            entryWithoutContent().copy(read = false),
            entryWithoutContent().copy(read = false),
            entryWithoutContent().copy(read = false),
        ).sortedByDescending { it.published }

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.read },
            repo.selectByRead(true).first(),
        )

        assertEquals(
            entries.filter { !it.read },
            repo.selectByRead(false).first(),
        )
    }
}