package entries

import api.NewsApi
import db.EntryQueries
import db.EntryWithoutSummary
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Entry
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

        mockkStatic("com.squareup.sqldelight.runtime.coroutines.FlowQuery")

        every { db.selectAll() } returns mockk {
            every { asFlow() } returns mockk {
                every { mapToList() } returns flowOf(entries)
            }
        }

        Assert.assertEquals(entries, repository.selectAll().first())

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

    private fun entry() = Entry(
        id = "",
        feedId = "",
        title = "",
        link = "",
        published = "",
        updated = "",
        authorName = "",
        content = "",
        enclosureLink = "",
        enclosureLinkType = "",
        opened = false,
        openedSynced = true,
        bookmarked = false,
        bookmarkedSynced = true,
        guidHash = "",
    )

    private fun entryWithoutSummary() = EntryWithoutSummary(
        id = "",
        feedId = "",
        title = "",
        link = "",
        published = "",
        updated = "",
        authorName = "",
        enclosureLink = "",
        enclosureLinkType = "",
        opened = false,
        openedSynced = true,
        bookmarked = false,
        bookmarkedSynced = true,
        guidHash = "",
    )
}