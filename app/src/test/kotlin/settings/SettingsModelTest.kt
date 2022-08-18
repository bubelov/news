package settings

import conf.ConfRepo
import db.testDb
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsModelTest {

    private val mainDispatcher = newSingleThreadContext("UI")

    @BeforeTest
    fun before() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun removeProtocolPrefixFromAccountName() = runBlocking {
        val db = testDb()
        val confRepo = ConfRepo(db)

        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                miniflux_server_url = "https://acme.com",
            )
        }

        var model = SettingsModel(
            confRepo = confRepo,
            db = db,
            syncScheduler = mockk(),
        )

        var state = model.state.filterIsInstance<SettingsModel.State.ShowingSettings>().first()

        assertEquals(
            expected = "@acme.com",
            state.logOutSubtitle,
        )

        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                miniflux_server_url = "http://acme.com",
            )
        }

        model = SettingsModel(
            confRepo = confRepo,
            db = db,
            syncScheduler = mockk(),
        )

        state = model.state.filterIsInstance<SettingsModel.State.ShowingSettings>().first()

        assertEquals(
            expected = "@acme.com",
            state.logOutSubtitle,
        )
    }
}