package feedsettings

import db.insertRandomFeed
import db.testDb
import feeds.FeedsRepo
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedSettingsModelTest {

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
    fun loadFeed(): Unit = runBlocking {
        val db = testDb()
        val feed = db.insertRandomFeed()

        val model = FeedSettingsModel(
            feedsRepo = FeedsRepo(mockk(), db),
        )

        assertEquals(FeedSettingsModel.State.LoadingFeed, model.state.value)
        model.feedId.update { feed.id }

        withTimeout(1000) {
            model.state.filterIsInstance<FeedSettingsModel.State.ShowingFeedSettings>().first()
        }
    }
}