package co.appreactor.news.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import kotlinx.android.synthetic.main.activity_app.*

class AppActivity : AppCompatActivity() {

    private val navController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val navListener = NavController.OnDestinationChangedListener { _, destination, _ ->
        bottomNavigation.isVisible = destination.id == R.id.entriesFragment
                || destination.id == R.id.feedsFragment
                || destination.id == R.id.bookmarksFragment
                || destination.id == R.id.settingsFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app)
    }

    override fun onStart() {
        super.onStart()
        bottomNavigation.setupWithNavController(navController)
        navController.addOnDestinationChangedListener(navListener)
    }
}