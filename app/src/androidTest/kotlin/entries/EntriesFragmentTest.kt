package entries

import androidx.fragment.app.testing.launchFragmentInContainer
import auth.AuthFragmentDirections
import co.appreactor.news.R
import kotlin.test.Test

class EntriesFragmentTest {

    @Test
    fun launch() {
        val directions =
            AuthFragmentDirections.actionAuthFragmentToEntriesFragment(EntriesFilter.NotBookmarked)

        launchFragmentInContainer<EntriesFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}