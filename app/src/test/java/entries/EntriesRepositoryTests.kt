package entries

import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.*
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.*

class EntriesRepositoryTests {

    private val api = mockk<NewsApi>()

    private val db = mockk<EntryQueries>()

    private val repository = EntriesRepository(
        api = api,
        db = db,
    )

    @Test
    fun selectAll(): Unit = runBlocking {
        val entries = listOf(entryWithoutSummary())

        every { db.selectAll() } returns mockk {
            every { executeAsList() } returns entries
        }

        Assert.assertEquals(entries, repository.selectAll())

        verify { db.selectAll() }

        confirmVerified(
            api,
            db,
        )
    }

    @Test
    fun selectById(): Unit = runBlocking {
        val entry = entry().copy(id = UUID.randomUUID().toString())

        every { db.selectById(entry.id) } returns mockk {
            every { executeAsOneOrNull() } returns entry
        }

        Assert.assertEquals(entry, repository.selectById(entry.id))

        verify { db.selectById(entry.id) }

        confirmVerified(db)
    }

    @Test
    fun selectByFeedId(): Unit = runBlocking {
        val feedId = UUID.randomUUID().toString()

        val entries = listOf(
            entryWithoutSummary().copy(feedId = feedId),
        )

        every { db.selectByFeedId(feedId) } returns mockk {
            every { executeAsList() } returns entries
        }

        Assert.assertEquals(entries, repository.selectByFeedId(feedId))

        verify { db.selectByFeedId(feedId) }

        confirmVerified(db)
    }

    @Test
    fun selectByReadOrBookmarked(): Unit = runBlocking {
        val read = false
        val bookmarked = true

        val entries = listOf(
            entryWithoutSummary(),
        )

        mockkStatic("com.squareup.sqldelight.runtime.coroutines.FlowQuery")

        every { db.selectByReadOrBookmarked(read, bookmarked) } returns mockk {
            every { asFlow() } returns mockk {
                every { mapToList() } returns flowOf(entries)
            }
        }

        Assert.assertEquals(entries, repository.selectByReadOrBookmarked(read, bookmarked).first())

        verify { db.selectByReadOrBookmarked(read, bookmarked) }

        confirmVerified(db)
    }
}