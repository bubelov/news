package feeds

import conf.ConfRepo
import db.testDb
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
    fun init(): Unit = runBlocking {
        val db = testDb()
        val confRepo = ConfRepo(db)
        val feedsRepo = FeedsRepo(mockk(), db)

        val model = FeedsModel(
            confRepo = confRepo,
            feedsRepo = feedsRepo,
        )

        withTimeout(1000) {
            model.state.filterIsInstance<FeedsModel.State.ShowingFeeds>()
        }
    }
}