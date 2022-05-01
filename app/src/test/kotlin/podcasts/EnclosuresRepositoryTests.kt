package podcasts

import db.EntryEnclosure
import db.database
import enclosures.EnclosuresRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

class EnclosuresRepositoryTests {

    @Test
    fun selectByEntryId(): Unit = runBlocking {
        val db = database()

        val repo = EnclosuresRepository(
            entryEnclosureQueries = db.entryEnclosureQueries,
            entriesRepo = mockk(),
            context = mockk(),
        )

        val enclosures = buildList {
            repeat(5) { add(entryEnclosure().copy(entryId = UUID.randomUUID().toString())) }
        }

        enclosures.forEach { db.entryEnclosureQueries.insertOrReplace(it) }

        assertEquals(enclosures.first(), repo.selectByEntryId(enclosures.first().entryId).first())
    }

    private fun entryEnclosure() = EntryEnclosure(
        entryId = "",
        downloadPercent = null,
        cacheUri = "",
    )
}