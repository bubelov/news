package common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityAppBinding
import entries.EntriesFilter
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
            val filter = args?.getParcelable<EntriesFilter>("filter")
            binding.bottomNavigation.isVisible = filter !is EntriesFilter.OnlyFromFeed
            return@OnDestinationChangedListener
        }

        if (destination.id == R.id.bookmarksFragment) {
            args!!.putParcelable("filter", EntriesFilter.OnlyBookmarked)
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

        binding.bottomNavigation.setOnNavigationItemReselectedListener {

        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.entriesFragment -> {
                    navController.navigate(
                        R.id.entriesFragment,
                        bundleOf(Pair("filter", EntriesFilter.OnlyNotBookmarked))
                    )
                }

                R.id.bookmarksFragment -> {
                    navController.navigate(
                        R.id.bookmarksFragment,
                        bundleOf(Pair("filter", EntriesFilter.OnlyBookmarked))
                    )
                }

                R.id.feedsFragment -> {
                    navController.navigate(R.id.feedsFragment)
                }

                R.id.settingsFragment -> {
                    navController.navigate(R.id.settingsFragment)
                }
            }

            false
        }

        if (inDarkMode()) {
            window.statusBarColor = getSurfaceColor(binding.bottomNavigation.elevation)
            window.navigationBarColor = getSurfaceColor(binding.bottomNavigation.elevation)
        }

        navController.addOnDestinationChangedListener(navListener)
    }
}