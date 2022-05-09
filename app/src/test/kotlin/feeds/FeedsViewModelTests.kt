package feeds

import common.ConfRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.newSingleThreadContext
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class FeedsViewModelTests {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun beforeEachTest() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @After
    fun afterEachTest() {
        Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun `init`(): Unit = runBlocking {
        val feedsRepo = mockk<FeedsRepository> {
            every { selectAll() } returns flowOf(emptyList())
        }

        val confRepo = mockk<ConfRepository> {
            every { select() } returns flowOf(ConfRepository.DEFAULT_CONF)
        }

        val model = FeedsViewModel(
            feedsRepo = feedsRepo,
            entriesRepo = mockk(),
            confRepo = confRepo,
        )

        var attempts = 0

        while (model.state.value !is FeedsViewModel.State.Loaded) {
            if (attempts++ > 100) {
                assertTrue { false }
            } else {
                delay(10)
            }
        }
    }
}