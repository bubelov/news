package co.appreactor.nextcloud.news

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import kotlinx.android.synthetic.main.activity_app.*


class AppActivity : AppCompatActivity() {

    private val navController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val navListener = NavController.OnDestinationChangedListener { _, destination, _ ->
        bottomNavigation.isVisible = destination.id == R.id.newsFragment
                || destination.id == R.id.starredNewsFragment
                || destination.id == R.id.settingsFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (navController.currentDestination?.id == R.id.authFragment) {
                        finish()
                        return
                    }

                    if (!navController.popBackStack()) {
                        finish()
                    }
                }
            })
    }

    override fun onStart() {
        super.onStart()
        bottomNavigation.setupWithNavController(navController)
        navController.addOnDestinationChangedListener(navListener)
    }
}