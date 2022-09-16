package auth

import conf.ConfRepo
import db.testDb
import io.mockk.coVerify
import io.mockk.mockk
import sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.assertEquals

class AuthModelTest {

    @Test
    fun setStandaloneBackend() {
        val db = testDb()
        val confRepo = ConfRepo(db)
        val syncScheduler = mockk<BackgroundSyncScheduler>(relaxUnitFun = true)

        val model = AuthModel(
            confRepo = confRepo,
            syncScheduler = syncScheduler,
        )

        model.setStandaloneBackend()

        val conf = confRepo.conf.value
        assertEquals(ConfRepo.BACKEND_STANDALONE, conf.backend)
        assertEquals(false, conf.sync_on_startup)
        assertEquals(TimeUnit.HOURS.toMillis(12), conf.background_sync_interval_millis)

        coVerify { syncScheduler.schedule() }
    }
}