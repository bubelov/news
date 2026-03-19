package org.vestifeed.enclosures

import co.appreactor.feedk.AtomLinkRel
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.Assert.assertTrue
import org.vestifeed.db.Link
import org.vestifeed.db.db
import org.vestifeed.db.entryWithoutContent

class EnclosuresRepoTest {

    @Test
    fun downloadAudioEnclosure_withInvalidRel(): Unit = runBlocking {
        val db = db()

        val repo = EnclosuresRepo(
            db = db,
            context = mockk(),
        )

        val entry = entryWithoutContent()

        val link = Link(
            feedId = null,
            entryId = entry.id,
            href = "https://localhost".toHttpUrl(),
            rel = AtomLinkRel.Alternate,
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )

        val res = runCatching { repo.downloadAudioEnclosure(link) }
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.startsWith("Invalid link rel") ?: false)
    }
}