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
    fun `default state`(): Unit = runBlocking {
        assertEquals(null, model.state.value)
    }

    @Test
    fun `view ready`(): Unit = runBlocking {
        coEvery { repository.selectAll() } returns emptyList()
        model.onViewReady()
        assertTrue(model.state.value is LogViewModel.State.Loaded)
    }

    @Test
    fun `view ready + db error`(): Unit = runBlocking {
        coEvery { repository.selectAll() } throws Exception()
        model.onViewReady()
        assertTrue(model.state.value is LogViewModel.State.FailedToLoad)
    }

    @Test
    fun `delete all`(): Unit = runBlocking {
        coEvery { repository.selectAll() } returns emptyList()

        model.deleteAll()

        coVerifySequence {
            repository.deleteAll()
            repository.selectAll()
        }

        confirmVerified(repository)
    }
}