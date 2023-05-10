package settings

import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Test

class SettingsFragmentTest {

    @Test
    fun launch() {
        launchFragmentInContainer<SettingsFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
        )
    }
}