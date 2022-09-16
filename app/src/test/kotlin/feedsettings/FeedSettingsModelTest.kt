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
import org.junit.After
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before

class FeedSettingsModelTest {

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