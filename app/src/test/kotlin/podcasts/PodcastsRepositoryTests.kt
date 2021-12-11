package podcasts

import android.content.Context
import db.*
import entries.EntriesRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.*

class PodcastsRepositoryTests {

    private val entriesRepository = mockk<EntriesRepository>()

    private val context = mockk<Context>()

    private val entryEnclosureQueries = mockk<EntryEnclosureQueries>()

    private val repository = PodcastsRepository(
        entriesRepository = entriesRepository,
        context = context,
        entryEnclosureQueries = entryEnclosureQueries,
    )

    @Test
    fun selectByEntryId(): Unit = runBlocking {
        val enclosure = entryEnclosure().copy(entryId = UUID.randomUUID().toString())

        every { entryEnclosureQueries.selectByEntryId(enclosure.entryId) } returns mockk {
            every { executeAsOneOrNull() } returns enclosure
        }

        Assert.assertEquals(enclosure, repository.selectByEntryId(enclosure.entryId))

        verify { entryEnclosureQueries.selectByEntryId(enclosure.entryId) }

        confirmVerified(entryEnclosureQueries)
    }

    private fun entryEnclosure() = EntryEnclosure(
        entryId = "",
        downloadPercent = null,
        cacheUri = "",
    )
}