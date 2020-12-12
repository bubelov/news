package feeds

import api.NewsApi
import co.appreactor.news.db.Feed
import co.appreactor.news.db.FeedQueries
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*

class FeedsRepositoryTests {

    private val cache = mockk<FeedQueries>(relaxUnitFun = true)

    private val api = mockk<NewsApi>(relaxUnitFun = true)

    private val repository = FeedsRepository(
        cache = cache,
        api = api,
    )

    @Test
    fun `updateTitle()`(): Unit = runBlocking {
        val feed = Feed(
            id = UUID.randomUUID().toString(),
            title = "Test",
            selfLink = "",
            alternateLink = "",
        )

        val newTitle = "  " + feed.title + "_modified "
        val trimmedNewTitle = newTitle.trim()

        every { cache.selectById(feed.id) } returns mockk {
            every { executeAsOneOrNull() } returns feed
        }

        repository.updateTitle(
            feedId = feed.id,
            newTitle = newTitle,
        )

        coVerifySequence {
            cache.selectById(feed.id)
            api.updateFeedTitle(feed.id, trimmedNewTitle)
            cache.insertOrReplace(feed.copy(title = trimmedNewTitle))
        }

        confirmVerified(
            api,
            cache,
        )
    }
}