package entries

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import auth.AuthFragmentDirections
import co.appreactor.news.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntriesFragmentTest {

    @Test
    fun resumesWithoutCrashes() {
        val directions =
            AuthFragmentDirections.actionAuthFragmentToEntriesFragment(EntriesFilter.NotBookmarked)

        launchFragmentInContainer<EntriesFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}