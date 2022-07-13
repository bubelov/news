package navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import entries.EntriesFilter

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

    private val navController by lazy { findNavController(R.id.navHost) }

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        binding.bottomNav.isVisible =
            destination.id == R.id.newsFragment || destination.id == R.id.bookmarksFragment || destination.id == R.id.feedsFragment

        when (destination.id) {
            R.id.newsFragment -> args!!.putParcelable("filter", EntriesFilter.NotBookmarked)
            R.id.bookmarksFragment -> args!!.putParcelable("filter", EntriesFilter.Bookmarked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        navController.addOnDestinationChangedListener(navListener)
        binding.bottomNav.setupWithNavController(navController)

        binding.bottomNav.setOnItemReselectedListener { item ->
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