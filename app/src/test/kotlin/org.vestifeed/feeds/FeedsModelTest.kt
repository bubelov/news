package org.vestifeed.feeds

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.db
import org.vestifeed.entries.EntriesRepo

class FeedsModelTest {

    private val mainDispatcher = newSingleThreadContext("UI")

    @Before
    fun before() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun init(): Unit = runBlocking {
        val db = db()
        val confRepo = ConfRepo(db)
        val feedsRepo = FeedsRepo(mockk(), db)
        val entriesRepo = EntriesRepo(mockk(), db)

        val model = FeedsModel(
            confRepo = confRepo,
            feedsRepo = feedsRepo,
            entriesRepo = entriesRepo,
        )

        withTimeout(1000) {
            model.state.filterIsInstance<FeedsModel.State.ShowingFeeds>()
        }
    }
}