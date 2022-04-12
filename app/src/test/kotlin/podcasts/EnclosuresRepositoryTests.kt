package podcasts

import android.content.Context
import db.EntryEnclosure
import db.EntryEnclosureQueries
import entries.EntriesRepository
import enclosures.EnclosuresRepository
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

class EnclosuresRepositoryTests {

    private val entriesRepository = mockk<EntriesRepository>()

    private val context = mockk<Context>()

    private val entryEnclosureQueries = mockk<EntryEnclosureQueries>()

    private val repository = EnclosuresRepository(
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

        assertEquals(enclosure, repository.selectByEntryId(enclosure.entryId))

        verify { entryEnclosureQueries.selectByEntryId(enclosure.entryId) }

        confirmVerified(entryEnclosureQueries)
    }

    private fun entryEnclosure() = EntryEnclosure(
        entryId = "",
        downloadPercent = null,
        cacheUri = "",
    )
}