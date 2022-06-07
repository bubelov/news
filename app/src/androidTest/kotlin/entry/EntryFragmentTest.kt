package entry

import androidx.fragment.app.testing.launchFragmentInContainer
import co.appreactor.news.R
import entries.EntriesFragmentDirections
import kotlin.test.Test

class EntryFragmentTest {

    @Test
    fun launch() {
        val directions =
            EntriesFragmentDirections.actionEntriesFragmentToEntryFragment("")

        launchFragmentInContainer<EntryFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}