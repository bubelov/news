package enclosures

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import co.appreactor.feedk.AtomLinkRel
import db.Link
import db.entry
import db.testDb
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class EnclosuresModelTest {

    private val mainDispatcher = newSingleThreadContext("UI")

    @BeforeTest
    fun before() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun deletePartialDownloads() = runBlocking {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()

        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        every { context.contentResolver } returns contentResolver
        every { contentResolver.delete(any(), null, null) } returns 1

        val db = testDb()

        val entryId = UUID.randomUUID().toString()
        val enclosureUri = "audio://1"

        val entry = entry().copy(
            id = entryId,
            links = listOf(
                Link(
                    feedId = null,
                    entryId = entryId,
                    href = "https://localhost".toHttpUrl(),
                    rel = AtomLinkRel.Enclosure,
                    type = "audio/mp3",
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = 0.5,
                    extCacheUri = enclosureUri,
                )
            )
        )

        db.entryQueries.insertOrReplace(entry)

        val enclosuresRepo = EnclosuresRepo(
            context = context,
            db = db,
        )

        val model = EnclosuresModel(
            db = db,
            enclosuresRepo = enclosuresRepo,
        )

        var attempts = 0

        while (model.state.value !is EnclosuresModel.State.ShowingEnclosures) {
            if (attempts++ > 100) {
                assertTrue { false }
            } else {
                delay(10)
            }
        }

        verify { contentResolver.delete(any(), null, null) }
    }
}