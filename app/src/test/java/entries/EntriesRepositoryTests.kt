package entries

import api.NewsApi
import db.EntryQueries
import db.EntryWithoutSummary
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EntriesRepositoryTests {

    private val api = mockk<NewsApi>()

    private val db = mockk<EntryQueries>()

    @Test
    fun selectAll(): Unit = runBlocking {
        val entries = listOf(
            EntryWithoutSummary(
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
        )

        mockkStatic("com.squareup.sqldelight.runtime.coroutines.FlowQuery")

        every { db.selectAll() } returns mockk {
            every { asFlow() } returns mockk {
                every { mapToList() } returns flowOf(entries)
            }
        }

        val repository = EntriesRepository(
            api = api,
            db = db,
        )

        assertEquals(entries, repository.selectAll().first())

        verify { db.selectAll() }

        confirmVerified(
            api,
            db,
        )
    }
}