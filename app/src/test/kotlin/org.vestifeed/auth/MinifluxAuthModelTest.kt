package org.vestifeed.auth

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.assertTrue
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.db

class MinifluxAuthModelTest {

    @Test
    fun testBackend() = runBlocking {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        val db = db()
        val confRepo = ConfRepo(db)

        val model = MinifluxAuthModel(
            confRepo = confRepo,
            syncScheduler = mockk(),
        )

        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.start()

        val res = runCatching {
            model.testBackend(
                url = server.url(""),
                token = "test-token",
                trustSelfSignedCerts = false,
            )
        }

        assertTrue(res.isSuccess)
    }
}