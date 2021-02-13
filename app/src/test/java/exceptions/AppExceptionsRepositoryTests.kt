package exceptions

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import db.LoggedException
import db.LoggedExceptionQueries
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppExceptionsRepositoryTests {

    private val db = mockk<LoggedExceptionQueries>(relaxed = true)

    private val repository = AppExceptionsRepository(db)

    @Test
    fun `insertOrReplace()`(): Unit = runBlocking {
        val item = LoggedException(
            id = "",
            date = "",
            exceptionClass = "",
            message = "",
            stackTrace = "",
        )

        repository.insertOrReplace(item)

        verify { db.insertOrReplace(item) }
        confirmVerified(db)
    }

    @Test
    fun `selectAll()`(): Unit = runBlocking {
        val items = listOf(
            LoggedException(
                id = "",
                date = "",
                exceptionClass = "",
                message = "",
                stackTrace = "",
            )
        )

        every { db.selectAll() } returns mockk {
            every { executeAsList() } returns items
        }

        assertEquals(items, repository.selectAll())

        verify { db.selectAll() }
        confirmVerified(db)
    }

    @Test
    fun `selectCount()`(): Unit = runBlocking {
        val count = 5L

        mockkStatic("com.squareup.sqldelight.runtime.coroutines.FlowQuery")

        every { db.selectCount() } returns mockk {
            every { asFlow() } returns mockk {
                every { mapToOne() } returns flowOf(count)
            }
        }

        assertEquals(count, repository.selectCount().first())

        verify { db.selectCount() }
        confirmVerified(db)
    }

    @Test
    fun `deleteAll()`(): Unit = runBlocking {
        repository.deleteAll()

        verify { db.deleteAll() }
        confirmVerified(db)
    }
}