package org.vestifeed.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.db.db
import org.vestifeed.db.table.ConfSchema

class HotSwapApiTest {

    @Test
    fun standaloneBackend() = runBlocking {
        val db = db()
        val api = HotSwapApi(db)

        db.conf.update { it.copy(backend = ConfSchema.BACKEND_STANDALONE) }

        var attempts = 0L

        while (attempts < 20 && runCatching { api.getFeeds() }.isFailure) {
            attempts += 1
            delay(10 * attempts)
        }

        assertEquals(0, api.getFeeds().size)
    }
}