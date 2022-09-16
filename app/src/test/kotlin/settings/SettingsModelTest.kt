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
import org.junit.After
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before

class SettingsModelTest {

    private val mainDispatcher = newSingleThreadContext("UI")

    @Before
    fun before() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
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

        assertEquals("@acme.com", state.logOutSubtitle)

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

        assertEquals("@acme.com", state.logOutSubtitle)
    }
}