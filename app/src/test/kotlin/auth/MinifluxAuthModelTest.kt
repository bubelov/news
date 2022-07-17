package auth

import android.util.Log
import conf.ConfRepo
import db.testDb
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertTrue

class MinifluxAuthModelTest {

    @Test
    fun testBackend() = runBlocking {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        val db = testDb()
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
                username = "test",
                password = "test",
                trustSelfSignedCerts = false,
            )
        }

        assertTrue(res.isSuccess)
    }
}