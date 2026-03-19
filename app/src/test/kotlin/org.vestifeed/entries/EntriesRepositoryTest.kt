package org.vestifeed.entries

import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.db.db
import org.vestifeed.db.entry
import org.vestifeed.db.entryWithoutContent
import org.vestifeed.db.insertRandomFeed
import org.vestifeed.db.toEntry

class EntriesRepositoryTest {

    @Test
    fun selectById(): Unit = runBlocking {
        val db = db()
        val repo = EntriesRepo(db = db, api = mockk())
        val entries = listOf(entry(), entry(), entry())
        entries.forEach { db.entryQueries.insertOrReplace(it) }
        val randomEntry = entries.random()
        assertEquals(randomEntry, repo.selectById(randomEntry.id).first())
    }

    @Test
    fun selectByReadAndBookmarked(): Unit = runBlocking {
        val db = db()
        val feed = db.insertRandomFeed()
        val repo = EntriesRepo(db = db, api = mockk())

        val entries = listOf(
            entryWithoutContent().copy(feedId = feed.id, extRead = true, extBookmarked = true),
            entryWithoutContent().copy(feedId = feed.id, extRead = true, extBookmarked = false),
            entryWithoutContent().copy(feedId = feed.id, extRead = false, extBookmarked = true),
            entryWithoutContent().copy(feedId = feed.id, extRead = false, extBookmarked = false),
        )

        entries.forEach { db.entryQueries.insertOrReplace(it.toEntry()) }

        assertEquals(
            entries.filter { it.extRead && it.extBookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = true).first()
                .map { it.id },
        )

        assertEquals(
            entries.filter { it.extRead && !it.extBookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(true), bookmarked = false).first()
                .map { it.id },
        )

        assertEquals(
            entries.filter { !it.extRead && it.extBookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = true).first()
                .map { it.id },
        )

        assertEquals(
            entries.filter { !it.extRead && !it.extBookmarked }.map { it.id },
            repo.selectByReadAndBookmarked(read = listOf(false), bookmarked = false).first()
                .map { it.id },
        )
    }
}