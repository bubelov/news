package navigation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import conf.ConfRepo
import entries.EntriesFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import opengraph.OpenGraphImagesRepo
import org.koin.android.ext.android.get

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

    private val navController: NavController by lazy { getSharedNavController()!! }

    private var currentDestinationId: Int = R.id.newsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.isVisible = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                v.updatePadding(bottom = it.bottom)
            }
            insets
        }

        get<ConfRepo>().update { it.copy(syncedOnStartup = false) }
        lifecycleScope.launch { get<OpenGraphImagesRepo>().fetchEntryImages() }

        // Initialize shared NavController - this must be done before any fragment calls findNavController()
        // The findNavController() in fragments will use this shared instance
        NavController.create(supportFragmentManager, R.id.navHost)
    }

    override fun onStart() {
        super.onStart()

        navController.addOnDestinationChangedListener { destinationId, args ->
            android.util.Log.d("Activity", "Destination changed: $destinationId")
            currentDestinationId = destinationId

            val showBottomNav = when (destinationId) {
                R.id.authFragment,
                R.id.minifluxAuthFragment,
                R.id.nextcloudAuthFragment,
                R.id.settingsFragment,
                R.id.enclosuresFragment,
                R.id.entryFragment,
                R.id.searchFragment,
                R.id.feedEntriesFragment,
                R.id.feedSettingsFragment -> false
                else -> true
            }

            android.util.Log.d("Activity", "showBottomNav: $showBottomNav, current visibility: ${binding.bottomNav.isVisible}")
            binding.bottomNav.isVisible = showBottomNav
            android.util.Log.d("Activity", "After set, visibility: ${binding.bottomNav.isVisible}")

            if (showBottomNav) {
                binding.bottomNav.menu.forEach { item ->
                    if (item.itemId == destinationId) {
                        item.isChecked = true
                    }
                }
            }

            when (destinationId) {
                R.id.newsFragment -> args?.putParcelable("filter", EntriesFilter.NotBookmarked)
                R.id.bookmarksFragment -> args?.putParcelable("filter", EntriesFilter.Bookmarked)
            }
        }

        navigateToStartDestination()

        binding.bottomNav.apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.newsFragment -> {
                        navController.navigate(R.id.newsFragment, NavDirections.AuthFragment.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked))
                        true
                    }
                    R.id.bookmarksFragment -> {
                        navController.navigate(R.id.bookmarksFragment, NavDirections.AuthFragment.actionAuthFragmentToNewsFragment(EntriesFilter.Bookmarked))
                        true
                    }
                    R.id.feedsFragment -> {
                        navController.navigate(R.id.feedsFragment)
                        true
                    }
                    else -> false
                }
            }
            setOnItemReselectedListener(createOnItemReselectedListener())
        }
    }

    private fun navigateToStartDestination() {
        lifecycleScope.launch {
            val confRepo = get<ConfRepo>()
            val config = confRepo.conf.value
            
            // Only navigate to news if already configured, otherwise go to auth
            if (config.minifluxServerUrl.isNotBlank() || config.nextcloudServerUrl.isNotBlank() || config.initialSyncCompleted) {
                navController.navigate(R.id.newsFragment, NavDirections.AuthFragment.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked))
            } else {
                navController.navigate(R.id.authFragment)
            }
        }
    }

    private fun updateBottomNavVisibility(destinationId: Int) {
        val showBottomNav = when (destinationId) {
            R.id.authFragment,
            R.id.minifluxAuthFragment,
            R.id.nextcloudAuthFragment,
            R.id.settingsFragment,
            R.id.enclosuresFragment,
            R.id.entryFragment,
            R.id.searchFragment,
            R.id.feedEntriesFragment,
            R.id.feedSettingsFragment -> false
            else -> true
        }
        binding.bottomNav.isVisible = showBottomNav
    }

    private fun createOnItemReselectedListener(): OnItemReselectedListener {
        return OnItemReselectedListener { item ->
            supportFragmentManager.fragments.forEach { fragment ->
                fragment.childFragmentManager.fragments.forEach {
                    (it as? OnItemReselectedListener)?.onNavigationItemReselected(item)
                }
            }
        }
    }
}
