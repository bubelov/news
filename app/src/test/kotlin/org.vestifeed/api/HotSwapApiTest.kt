package org.vestifeed.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.db

class HotSwapApiTest {

    @Test
    fun standaloneBackend() = runBlocking {
        val db = db()
        val confRepo = ConfRepo(db)
        val api = HotSwapApi(confRepo, db)

        confRepo.update { it.copy(backend = ConfQueries.BACKEND_STANDALONE) }

        var attempts = 0L

        while (attempts < 20 && runCatching { api.getFeeds() }.isFailure) {
            attempts += 1
            delay(10 * attempts)
        }

        assertEquals(0, api.getFeeds().getOrThrow().size)
    }
}