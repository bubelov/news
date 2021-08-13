package log

import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogViewModelTests {

    private val repository = mockk<LogRepository>(relaxed = true)

    private val model = LogViewModel(repository)

    @Test
    fun `Should be idle by default`(): Unit = runBlocking {
        assertEquals(LogViewModel.State.Idle, model.state.value)
    }

    @Test
    fun `Should query items when view is ready`(): Unit = runBlocking {
        coEvery { repository.selectAll() } returns emptyList()
        model.onViewReady()
        assertTrue(model.state.value is LogViewModel.State.Loaded)
    }

//    @Test
//    fun `Should return error if can't query items`(): Unit = runBlocking {
//        coEvery { repository.selectAll() } throws Exception()
//        model.onViewReady()
//        assertTrue(model.items.value is Result.Failure)
//    }

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