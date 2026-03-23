package org.vestifeed.auth

import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.db
import org.vestifeed.sync.BackgroundSyncScheduler

class AuthModelTest {

    @Test
    fun setStandaloneBackend() {
        val db = db()
        val syncScheduler = mockk<BackgroundSyncScheduler>(relaxUnitFun = true)

        val model = AuthModel(
            db = db,
            syncScheduler = syncScheduler,
        )

        model.setStandaloneBackend()

        val conf = db.confQueries.select()
        assertEquals(ConfQueries.BACKEND_STANDALONE, conf.backend)
        assertEquals(false, conf.syncOnStartup)
        assertEquals(TimeUnit.HOURS.toMillis(12), conf.backgroundSyncIntervalMillis)

        coVerify { syncScheduler.schedule() }
    }
}