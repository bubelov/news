package co.appreactor.news.entries

import co.appreactor.news.api.NewsApi
import co.appreactor.news.common.Preferences
import co.appreactor.news.db.EntryQueries
import co.appreactor.news.db.EntryWithoutSummary
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EntriesRepositoryTests {

    private val entryQueries = mockk<EntryQueries>()

    private val api = mockk<NewsApi>()

    private val prefs = mockk<Preferences>()

    @Test
    fun `getAll()`(): Unit = runBlocking {
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

        every { entryQueries.selectAll() } returns mockk {
            every { asFlow() } returns mockk {
                every { mapToList() } returns flowOf(entries)
            }
        }

        val repository = EntriesRepository(
            entryQueries = entryQueries,
            newsApi = api,
            prefs = prefs,
        )

        assertEquals(entries, repository.getAll().first())

        verify { entryQueries.selectAll() }

        confirmVerified(
            entryQueries,
            api,
            prefs,
        )
    }
}