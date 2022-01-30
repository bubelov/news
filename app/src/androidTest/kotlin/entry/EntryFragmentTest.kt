package entry

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.appreactor.news.R
import entries.EntriesFragmentDirections
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryFragmentTest {

    @Test
    fun resumesWithoutCrashes() {
        val directions =
            EntriesFragmentDirections.actionEntriesFragmentToEntryFragment("")

        launchFragmentInContainer<EntryFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}