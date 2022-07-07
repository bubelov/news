package navigation

import android.app.Application
import android.content.res.Resources
import conf.ConfRepo
import db.testDb
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityModelTest {

    @Test
    fun getAccountTitle() {
        val title = "Title"

        val app = mockk<Application>()
        val resources = mockk<Resources>()
        every { resources.getString(any()) } returns title
        every { app.resources } returns resources

        val db = testDb()
        val confRepo = ConfRepo(db)

        val model = ActivityModel(
            app = app,
            confRepo = confRepo,
        )

        confRepo.update { it.copy(backend = ConfRepo.BACKEND_STANDALONE) }

        assertEquals(title, runBlocking { model.accountTitle().first() })

        verify { app.resources }
    }

    @Test
    fun getAccountSubtitle() {
        val username = "test"

        val db = testDb()
        val confRepo = ConfRepo(db)

        val model = ActivityModel(
            app = mockk(),
            confRepo = confRepo,
        )

        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_MINIFLUX,
                minifluxServerUsername = "test",
            )
        }

        assert(runBlocking { model.accountSubtitle().first() }.contains(username))
    }
}