package logentries

import db.LogEntryQueries
import db.logEntry
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntriesRepositoryTests {

    private val db = mockk<LogEntryQueries>(relaxed = true)

    private val repository = LogEntriesRepository(db)

    @Test
    fun insert(): Unit = runBlocking {
        val item = logEntry()
        repository.insert(item)
        verify { db.insert(item) }
        confirmVerified(db)
    }

    @Test
    fun selectAll(): Unit = runBlocking {
        val items = listOf(logEntry(), logEntry())

        every { db.selectAll() } returns mockk {
            every { executeAsList() } returns items
        }

        assertEquals(items, repository.selectAll())

        verify { db.selectAll() }
        confirmVerified(db)
    }

    @Test
    fun deleteAll(): Unit = runBlocking {
        repository.deleteAll()
        verify { db.deleteAll() }
        confirmVerified(db)
    }
}