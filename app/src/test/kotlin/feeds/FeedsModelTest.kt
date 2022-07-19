package feeds

import conf.ConfRepo
import db.testDb
import entries.EntriesRepo
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeedsModelTest {

    private val mainDispatcher = newSingleThreadContext("UI")

    @BeforeTest
    fun before() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun init() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepo(db)
        val entriesRepo = EntriesRepo(mockk(), db)
        val feedsRepo = FeedsRepo(db, mockk())

        val model = FeedsModel(
            confRepo = confRepo,
            db = db,
            entriesRepo = entriesRepo,
            feedsRepo = feedsRepo,
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