package auth

import conf.ConfRepo
import db.testDb
import io.mockk.coVerify
import io.mockk.mockk
import sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthModelTest {

    @Test
    fun hasBackend() {
        val db = testDb()
        val confRepo = ConfRepo(db)

        val model = AuthModel(
            confRepo = confRepo,
            syncScheduler = mockk(),
        )

        assertEquals(model.hasBackend(), false)
        confRepo.update { it.copy(backend = ConfRepo.BACKEND_STANDALONE) }
        assertEquals(model.hasBackend(), true)
    }

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
        assertEquals(false, conf.syncOnStartup)
        assertEquals(TimeUnit.HOURS.toMillis(12), conf.backgroundSyncIntervalMillis)

        coVerify { syncScheduler.schedule() }
    }
}