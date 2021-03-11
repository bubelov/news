package exceptions

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import common.Result
import org.junit.Assert.*

class AppExceptionsViewModelTests {

    private val repository = mockk<AppExceptionsRepository>(relaxed = true)

    private val model = AppExceptionsViewModel(repository)

    @Test
    fun `Should be inactive by default`(): Unit = runBlocking {
        assertEquals(Result.Inactive, model.items.value)
    }

    @Test
    fun `Should query items when view is ready`(): Unit = runBlocking {
        coEvery { repository.selectAll() } returns emptyList()
        model.onViewReady()
        assertTrue(model.items.value is Result.Success)
    }

    @Test
    fun `Should return error if can't query items`(): Unit = runBlocking {
        coEvery { repository.selectAll() } throws Exception()
        model.onViewReady()
        assertTrue(model.items.value is Result.Failure)
    }

    @Test
    fun `Calling deleteAllItems() should delete all items`(): Unit = runBlocking {
        coEvery { repository.selectAll() } returns emptyList()

        model.deleteAllItems()

        coVerifySequence {
            repository.deleteAll()
            repository.selectAll()
        }

        confirmVerified(repository)
    }
}