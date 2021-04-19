package exceptions

import db.LoggedExceptionQueries
import db.appException
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppExceptionsRepositoryTests {

    private val db = mockk<LoggedExceptionQueries>(relaxed = true)

    private val repository = AppExceptionsRepository(db)

    @Test
    fun insert(): Unit = runBlocking {
        val item = appException()
        repository.insert(item)
        verify { db.insert(item) }
        confirmVerified(db)
    }

    @Test
    fun selectAll(): Unit = runBlocking {
        val items = listOf(appException(), appException())

        every { db.selectAll() } returns mockk {
            every { executeAsList() } returns items
        }

        assertEquals(items, repository.selectAll())

        verify { db.selectAll() }
        confirmVerified(db)
    }

    @Test
    fun selectById(): Unit = runBlocking {
        val item = appException()

        every { db.selectById(item.id) } returns mockk {
            every { executeAsOneOrNull() } returns item
        }

        assertEquals(item, repository.selectById(item.id))

        verify { db.selectById(item.id) }
        confirmVerified(db)
    }

    @Test
    fun deleteAll(): Unit = runBlocking {
        repository.deleteAll()
        verify { db.deleteAll() }
        confirmVerified(db)
    }
}