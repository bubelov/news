package navigation

import android.app.Application
import android.content.res.Resources
import conf.ConfRepository
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
    fun getAccountTitle() = runBlocking {
        val title = "Title"

        val app = mockk<Application>()
        val resources = mockk<Resources>()
        every { resources.getString(any()) } returns title
        every { app.resources } returns resources

        val db = testDb()
        val confRepo = ConfRepository(db)

        val model = ActivityModel(
            app = app,
            confRepo = confRepo,
        )

        confRepo.save { it.copy(backend = ConfRepository.BACKEND_STANDALONE) }

        assertEquals(title, model.accountTitle().first())

        verify { app.resources }
    }

    @Test
    fun getAccountSubtitle() = runBlocking {
        val username = "test"

        val db = testDb()
        val confRepo = ConfRepository(db)

        val model = ActivityModel(
            app = mockk(),
            confRepo = confRepo,
        )

        confRepo.save {
            it.copy(
                backend = ConfRepository.BACKEND_MINIFLUX,
                minifluxServerUsername = "test",
            )
        }

        assert(model.accountSubtitle().first().contains(username))
    }
}