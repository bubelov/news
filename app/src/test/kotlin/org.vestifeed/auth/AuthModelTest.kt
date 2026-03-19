package org.vestifeed.auth

import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.assertEquals
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.db
import org.vestifeed.sync.BackgroundSyncScheduler

class AuthModelTest {

    @Test
    fun setStandaloneBackend() {
        val db = db()
        val confRepo = ConfRepo(db)
        val syncScheduler = mockk<BackgroundSyncScheduler>(relaxUnitFun = true)

        val model = AuthModel(
            confRepo = confRepo,
            syncScheduler = syncScheduler,
        )

        model.setStandaloneBackend()

        val conf = confRepo.conf.value
        assertEquals(ConfRepo.BACKEND_STANDALONE, conf.backend)
        assertEquals(false, conf.syncOnStartup)
        assertEquals(TimeUnit.HOURS.toMillis(12), conf.backgroundSyncIntervalMillis)

        coVerify { syncScheduler.schedule() }
    }
}