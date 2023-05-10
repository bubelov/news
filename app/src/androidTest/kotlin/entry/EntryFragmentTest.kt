package entry

import androidx.fragment.app.testing.launchFragmentInContainer
import entries.EntriesFragmentDirections
import org.junit.Test

class EntryFragmentTest {

    @Test
    fun launch() {
        val directions =
            EntriesFragmentDirections.actionEntriesFragmentToEntryFragment("")

        launchFragmentInContainer<EntryFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = directions.arguments,
        )
    }
}