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

    private val db = mockk<EntryEnclosureQueries>()

    private val context = mockk<Context>()

    private val repository = PodcastsRepository(
        entriesRepository = entriesRepository,
        db = db,
        context = context,
    )

    @Test
    fun selectByEntryId(): Unit = runBlocking {
        val enclosure = entryEnclosure().copy(entryId = UUID.randomUUID().toString())

        every { db.selectByEntryId(enclosure.entryId) } returns mockk {
            every { executeAsOneOrNull() } returns enclosure
        }

        Assert.assertEquals(enclosure, repository.selectByEntryId(enclosure.entryId))

        verify { db.selectByEntryId(enclosure.entryId) }

        confirmVerified(db)
    }

    private fun entryEnclosure() = EntryEnclosure(
        entryId = "",
        downloadPercent = null,
        cacheUri = "",
    )
}