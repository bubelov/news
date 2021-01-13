package common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityAppBinding
import entriesenclosures.EntriesEnclosuresRepository
import entriesimages.EntriesImagesRepository
import org.koin.android.ext.android.get

class AppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppBinding

    private val navController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        if (destination.id == R.id.entriesFragment) {
            val feedId = args?.getString("feedId")
            binding.bottomNavigation.isVisible = feedId.isNullOrEmpty()
            return@OnDestinationChangedListener
        }

        binding.bottomNavigation.isVisible =
            destination.id == R.id.feedsFragment
                    || destination.id == R.id.bookmarksFragment
                    || destination.id == R.id.settingsFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launchWhenCreated {
            get<EntriesEnclosuresRepository>().apply {
                deleteDownloadedEnclosuresWithoutFiles()
                deletePartialDownloads()
            }
        }

        lifecycleScope.launchWhenCreated {
            get<EntriesImagesRepository>().syncPreviews()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.bottomNavigation.setupWithNavController(navController)

        if (inDarkMode()) {
            window.statusBarColor = getSurfaceColor(binding.bottomNavigation.elevation)
            window.navigationBarColor = getSurfaceColor(binding.bottomNavigation.elevation)
        }

        navController.addOnDestinationChangedListener(navListener)
    }
}