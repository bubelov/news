package log

import db.Log
import db.LogQueries
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class LogRepositoryTests {

    private val db = mockk<LogQueries>(relaxed = true)

    private val repo = LogRepository(db)

    @Test
    fun insert(): Unit = runBlocking {
        val row = log()

        repo.insert(
            date = row.date,
            level = row.level,
            tag = row.tag,
            message = row.message,
            stackTrace = row.stackTrace,
        )

        verify {
            db.insert(
                date = row.date,
                level = row.level,
                tag = row.tag,
                message = row.message,
                stackTrace = row.stackTrace,
            )
        }

        confirmVerified(db)
    }

    @Test
    fun `select all`(): Unit = runBlocking {
        val rows = listOf(log(), log())

        every { db.selectAll() } returns mockk {
            every { executeAsList() } returns rows
        }

        assertEquals(rows, repo.selectAll())

        verify { db.selectAll() }
        confirmVerified(db)
    }

    @Test
    fun `select by id`() = runBlocking {
        val row = log()

        every { db.selectById(row.id) } returns mockk {
            every { executeAsOneOrNull() } returns row
        }

        assertEquals(row, repo.selectById(row.id))

        verify { db.selectById(row.id) }
        confirmVerified(db)
    }

    @Test
    fun `delete all`(): Unit = runBlocking {
        repo.deleteAll()
        verify { db.deleteAll() }
        confirmVerified(db)
    }

    @Test
    fun `delete older than`(): Unit = runBlocking {
        every { db.selectCount() } returns mockk {
            every { executeAsOne() } returns 0
        }

        repo.deleteOlderThan(Duration.ofDays(5))

        verify {
            db.selectCount()
            db.deleteWhereDateLessThan(any())
            db.selectCount()
        }

        confirmVerified(db)
    }

    private fun log() = Log(
        id = 0,
        date = "",
        level = 0,
        tag = "",
        message = "",
        stackTrace = "",
    )
}