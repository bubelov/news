package org.vestifeed.settings

import android.app.Application
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
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.db

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
        val app: Application = mockk(relaxed = true)

        val db = db()

        db.confQueries.update {
            it.copy(
                backend = ConfQueries.BACKEND_MINIFLUX,
                minifluxServerUrl = "https://acme.com",
            )
        }

        var model = SettingsModel(
            app = app,
            db = db,
            syncScheduler = mockk(),
        )

        var state = model.state.filterIsInstance<SettingsModel.State.ShowingSettings>().first()

        assertEquals("acme.com", state.logOutSubtitle)

        db.confQueries.update {
            it.copy(
                backend = ConfQueries.BACKEND_MINIFLUX,
                minifluxServerUrl = "http://acme.com",
            )
        }

        model = SettingsModel(
            app = app,
            db = db,
            syncScheduler = mockk(),
        )

        state = model.state.filterIsInstance<SettingsModel.State.ShowingSettings>().first()

        assertEquals("acme.com", state.logOutSubtitle)
    }
}