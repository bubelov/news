package log

import db.LogQueries
import db.log
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryTests {

    private val db = mockk<LogQueries>(relaxed = true)

    private val repository = LogRepository(db)

    @Test
    fun insert(): Unit = runBlocking {
        val item = log()
        repository.insert(item)
        verify { db.insert(item) }
        confirmVerified(db)
    }

    @Test
    fun selectAll(): Unit = runBlocking {
        val items = listOf(log(), log())

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