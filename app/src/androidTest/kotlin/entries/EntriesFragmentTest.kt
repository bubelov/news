package entries

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.platform.app.InstrumentationRegistry
import auth.AuthFragmentDirections
import conf.ConfRepo
import db.db
import org.junit.Test

class EntriesFragmentTest {

    @Test
    fun launch() {
        val db = db(InstrumentationRegistry.getInstrumentation().targetContext)
        val confRepo = ConfRepo(db)
        confRepo.update { it.copy(backend = ConfRepo.BACKEND_STANDALONE) }

        val directions =
            AuthFragmentDirections.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked)

        launchFragmentInContainer<EntriesFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}