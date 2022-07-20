package feedsettings

import db.feed
import db.testDb
import feeds.FeedsRepo
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun loadFeed() = runBlocking {
        val db = testDb()
        val feedsRepo = FeedsRepo(mockk(), db)

        val feed = feed()
        feedsRepo.insertOrReplace(feed)

        val model = FeedSettingsModel(
            feedsRepo = feedsRepo,
        )

        assertEquals(FeedSettingsModel.State.LoadingFeed, model.state.value)
        model.feedId.update { feed.id }

        var attempts = 0

        while (model.state.value !is FeedSettingsModel.State.ShowingFeedSettings) {
            if (attempts++ > 100) {
                assertTrue { false }
            } else {
                delay(10)
            }
        }
    }
}