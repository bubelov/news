package feeds

import android.content.res.Resources
import common.ConfRepository
import entries.EntriesRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FeedsViewModelTests {

    private val feedsRepo = mockk<FeedsRepository>(relaxed = true)
    private val entriesRepo = mockk<EntriesRepository>(relaxed = true)
    private val confRepo = mockk<ConfRepository>(relaxed = true)
    private val resources = mockk<Resources>(relaxed = true)

    private val model = FeedsViewModel(
        feedsRepo = feedsRepo,
        entriesRepo = entriesRepo,
        confRepository = confRepo,
        resources = resources,
    )

    @Test
    fun `default state`(): Unit = runBlocking {
        assertEquals(null, model.state.value)
    }

    @Test
    fun `view ready`(): Unit = runBlocking {
        coEvery { feedsRepo.selectAll() } returns emptyList()
        model.onViewCreated()

        model.state.value.apply {
            assertTrue(this is FeedsViewModel.State.Loaded && result.isSuccess)
        }
    }

    @Test
    fun `view ready + db error`(): Unit = runBlocking {
        coEvery { feedsRepo.selectAll() } throws Exception()
        model.onViewCreated()

        model.state.value.apply {
            assertTrue(this is FeedsViewModel.State.Loaded && result.isFailure)
        }
    }
}