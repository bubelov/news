package api

import conf.ConfRepository
import db.testDb
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HotSwapApiTest {

    @Test
    fun standaloneBackend() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepository(db)
        val api = HotSwapApi(confRepo, db)

        confRepo.save { it.copy(backend = ConfRepository.BACKEND_STANDALONE) }

        var attempts = 0L

        while (attempts < 20 && runCatching { api.getFeeds() }.isFailure) {
            attempts += 1
            delay(10 * attempts)
        }

        assertEquals(0, api.getFeeds().getOrThrow().size)
    }
}