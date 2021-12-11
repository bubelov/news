package log

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogFragmentTest {

    @Test
    fun resumesWithoutCrashes() {
        launchFragmentInContainer<LogFragment>()
    }
}