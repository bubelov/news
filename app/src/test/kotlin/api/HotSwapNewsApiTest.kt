package api

import api.standalone.StandaloneNewsApi
import common.ConfRepository
import db.testDb
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class HotSwapNewsApiTest {

    @Test
    fun standaloneBackend() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepository(db)
        val api = HotSwapNewsApi(confRepo, db)

        confRepo.save { it.copy(backend = ConfRepository.BACKEND_STANDALONE) }

        var attempts = 0L

        while (attempts < 20 && runCatching { api.api }.isFailure) {
            attempts += 1
            delay(10 * attempts)
        }

        assert(api.api is StandaloneNewsApi)
    }
}