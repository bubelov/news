package feeds

import db.testDb
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class FeedsModelTests {

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
    fun init(): Unit = runBlocking {
        val db = testDb()

        val model = FeedsModel(
            app = mockk(),
            confRepo = mockk(),
            db = db,
            api = mockk(),
        )

        var attempts = 0

        while (model.state.value !is FeedsModel.State.ShowingFeeds) {
            if (attempts++ > 100) {
                assertTrue { false }
            } else {
                delay(10)
            }
        }
    }
}