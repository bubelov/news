package auth

import conf.ConfRepository
import db.testDb
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthModelTest {

    @Test
    fun hasBackend() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepository(db)

        val model = AuthModel(
            confRepo = confRepo,
            syncScheduler = mockk(),
        )

        assertEquals(model.hasBackend(), false)
        confRepo.save { it.copy(backend = ConfRepository.BACKEND_STANDALONE) }
        assertEquals(model.hasBackend(), true)
    }

    @Test
    fun setStandaloneBackend() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepository(db)
        val syncScheduler = mockk<BackgroundSyncScheduler>(relaxUnitFun = true)

        val model = AuthModel(
            confRepo = confRepo,
            syncScheduler = syncScheduler,
        )

        model.setStandaloneBackend()

        val conf = confRepo.load().first()
        assertEquals(ConfRepository.BACKEND_STANDALONE, conf.backend)
        assertEquals(false, conf.syncOnStartup)
        assertEquals(TimeUnit.HOURS.toMillis(12), conf.backgroundSyncIntervalMillis)

        coVerify { syncScheduler.schedule(override = true) }
    }
}