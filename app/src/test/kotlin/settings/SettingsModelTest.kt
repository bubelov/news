package settings

import conf.ConfRepo
import db.testDb
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsModelTest {

    @Test
    fun removeProtocolPrefixFromAccountName() {
        val db = testDb()
        val confRepo = ConfRepo(db)

        val model = SettingsModel(
            confRepo = confRepo,
            db = db,
            syncScheduler = mockk(),
        )

        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                minifluxServerUrl = "https://acme.com",
            )
        }

        assertEquals(
            expected = "@acme.com",
            model.getAccountName(),
        )

        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                minifluxServerUrl = "http://acme.com",
            )
        }
        
        assertEquals(
            expected = "@acme.com",
            model.getAccountName(),
        )
    }
}