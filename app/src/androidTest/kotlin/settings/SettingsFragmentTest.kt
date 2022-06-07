package settings

import androidx.fragment.app.testing.launchFragmentInContainer
import co.appreactor.news.R
import kotlin.test.Test

class SettingsFragmentTest {

    @Test
    fun launch() {
        launchFragmentInContainer<SettingsFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight,
        )
    }
}