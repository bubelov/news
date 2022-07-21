package navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import conf.ConfRepo
import entries.EntriesFilter
import kotlinx.coroutines.launch
import opengraph.OpenGraphImagesRepository
import org.koin.android.ext.android.get

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

    private val navController by lazy { findNavController(R.id.navHost) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        get<ConfRepo>().update { it.copy(syncedOnStartup = false) }
        lifecycleScope.launch { get<OpenGraphImagesRepository>().fetchEntryImages() }
    }

    override fun onStart() {
        super.onStart()

        navController.addOnDestinationChangedListener(createNavDestChangedListener())

        binding.bottomNav.apply {
            setOnItemSelectedListener { NavigationUI.onNavDestinationSelected(it, navController) }
            setOnItemReselectedListener(createOnItemReselectedListener())
        }
    }

    private fun createNavDestChangedListener(): NavController.OnDestinationChangedListener {
        return NavController.OnDestinationChangedListener { _, destination, args ->
            binding.bottomNav.apply {
                isVisible = menu.findItem(destination.id) != null

                menu.forEach { item ->
                    if (destination.hierarchy.any { it.id == item.itemId }) {
                        item.isChecked = true
                    }
                }
            }

            when (destination.id) {
                R.id.newsFragment -> args!!.putParcelable("filter", EntriesFilter.NotBookmarked)
                R.id.bookmarksFragment -> args!!.putParcelable("filter", EntriesFilter.Bookmarked)
            }
        }
    }

    private fun createOnItemReselectedListener(): OnItemReselectedListener {
        return OnItemReselectedListener { item ->
            supportFragmentManager.fragments.forEach { fragment ->
                if (fragment is NavHostFragment) {
                    fragment.childFragmentManager.fragments.forEach {
                        (it as? OnItemReselectedListener)?.onNavigationItemReselected(item)
                    }
                }
            }
        }
    }
}