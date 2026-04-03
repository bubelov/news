package org.vestifeed.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.db

class HotSwapApiTest {

    @Test
    fun standaloneBackend() = runBlocking {
        val db = db()
        val api = HotSwapApi(db)

        db.conf.update { it.copy(backend = ConfQueries.BACKEND_STANDALONE) }

        var attempts = 0L

        while (attempts < 20 && runCatching { api.getFeeds() }.isFailure) {
            attempts += 1
            delay(10 * attempts)
        }

        assertEquals(0, api.getFeeds().size)
    }
}