package entries

import db.entry
import db.entryWithoutContent
import db.insertRandomFeed
import db.testDb
import db.toEntry
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EntriesRepositoryTest {

    @Test
    fun selectById(): Unit = runBlocking {
        val db = testDb()
        val repo = EntriesRepo(db = db, api = mockk())
        val entries = listOf(entry(), entry(), entry())
        entries.forEach { db.entryQueries.insertOrReplace(it) }
        val randomEntry = entries.random()
        assertEquals(randomEntry, repo.selectById(randomEntry.id).first())
    }

    @Test
    fun selectByReadAndBookmarked(): Unit = runBlocking {
        val db = testDb()
        val feed = db.insertRandomFeed()
        val repo = EntriesRepo(db = db, api = mockk())

        val entries = listOf(
            entryWithoutContent().copy(feed_id = feed.id, ext_read = true, ext_bookmarked = true),
            entryWithoutContent().copy(feed_id = feed.id, ext_read = true, ext_bookmarked = false),
            entryWithoutContent().copy(feed_id = feed.id, ext_read = false, ext_bookmarked = true),
            entryWithoutContent().copy(feed_id = feed.id, ext_read = false, ext_bookmarked = false),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.ext_read && it.ext_bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = true).first().map { it.id },
        )

        assertEquals(
            entries.filter { it.ext_read && !it.ext_bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = false).first().map { it.id },
        )

        assertEquals(
            entries.filter { !it.ext_read && it.ext_bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = true).first().map { it.id },
        )

        assertEquals(
            entries.filter { !it.ext_read && !it.ext_bookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = false).first().map { it.id },
        )
    }

    @Test
    fun selectByReadOrBookmarked(): Unit = runBlocking {
        val db = testDb()
        val repo = EntriesRepo(db = db, api = mockk())

        val entries = listOf(
            entryWithoutContent().copy(ext_read = true, ext_bookmarked = true),
            entryWithoutContent().copy(ext_read = true, ext_bookmarked = false),
            entryWithoutContent().copy(ext_read = false, ext_bookmarked = true),
            entryWithoutContent().copy(ext_read = false, ext_bookmarked = false),
        ).sortedByDescending { it.published }

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.ext_read || it.ext_bookmarked },
            repo.selectByReadOrBookmarked(read = true, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { it.ext_read || !it.ext_bookmarked },
            repo.selectByReadOrBookmarked(read = true, bookmarked = false).first(),
        )

        assertEquals(
            entries.filter { !it.ext_read || it.ext_bookmarked },
            repo.selectByReadOrBookmarked(read = false, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { !it.ext_read || !it.ext_bookmarked },
            repo.selectByReadOrBookmarked(read = false, bookmarked = false).first(),
        )
    }

    @Test
    fun selectByRead(): Unit = runBlocking {
        val db = testDb()
        val repo = EntriesRepo(db = db, api = mockk())

        val entries = listOf(
            entryWithoutContent().copy(ext_read = true),
            entryWithoutContent().copy(ext_read = false),
            entryWithoutContent().copy(ext_read = false),
            entryWithoutContent().copy(ext_read = false),
        ).sortedByDescending { it.published }

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.ext_read },
            repo.selectByRead(true).first(),
        )

        assertEquals(
            entries.filter { !it.ext_read },
            repo.selectByRead(false).first(),
        )
    }
}