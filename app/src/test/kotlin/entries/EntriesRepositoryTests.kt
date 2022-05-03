package entries

import db.database
import db.entry
import db.entryWithoutContent
import db.toEntry
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class EntriesRepositoryTests {

    @Test
    fun selectAll(): Unit = runBlocking {
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
        )

        val entries = listOf(entryWithoutContent())
        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(entries, repo.selectAll().first())
    }

    @Test
    fun selectById(): Unit = runBlocking {
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
        )

        val entries = listOf(entry(), entry(), entry())
        entries.forEach { db.entryQueries.insertOrReplace(it) }

        val randomEntry = entries.random()

        assertEquals(randomEntry, repo.selectById(randomEntry.id).first())
    }

    @Test
    fun selectByFeedId(): Unit = runBlocking {
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
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
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
        )

        val entries = listOf(
            entryWithoutContent().copy(read = true, bookmarked = true),
            entryWithoutContent().copy(read = true, bookmarked = false),
            entryWithoutContent().copy(read = false, bookmarked = true),
            entryWithoutContent().copy(read = false, bookmarked = false),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.read && it.bookmarked },
            repo.selectByReadAndBookmarked(read = true, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { it.read && !it.bookmarked },
            repo.selectByReadAndBookmarked(read = true, bookmarked = false).first(),
        )

        assertEquals(
            entries.filter { !it.read && it.bookmarked },
            repo.selectByReadAndBookmarked(read = false, bookmarked = true).first(),
        )

        assertEquals(
            entries.filter { !it.read && !it.bookmarked },
            repo.selectByReadAndBookmarked(read = false, bookmarked = false).first(),
        )
    }

    @Test
    fun selectByReadOrBookmarked(): Unit = runBlocking {
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
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
        val db = database()

        val repo = EntriesRepository(
            api = mockk(),
            db = db.entryQueries,
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