package navigation

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import entries.EntriesFilter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import org.koin.androidx.viewmodel.ext.android.viewModel

class Activity : AppCompatActivity() {

    private val model: ActivityModel by viewModel()

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
        initDrawerHeader()
    }

    override fun onStart() {
        super.onStart()
        navController.addOnDestinationChangedListener(navListener)
        binding.navView.setupWithNavController(navController)
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

    fun setupDrawerToggle(toolbar: MaterialToolbar) {
        val drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            toolbar,
            R.string.open,
            R.string.close,
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)

        drawerToggle.syncState()
    }

    private fun initDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)!!
        val titleView = headerView.findViewById<TextView>(R.id.title)!!
        val subtitleView = headerView.findViewById<TextView>(R.id.subtitle)!!

        combine(model.accountTitle(), model.accountSubtitle()) { title, subtitle ->
            titleView.text = title
            subtitleView.isVisible = subtitle.isNotBlank()
            subtitleView.text = subtitle
        }.launchIn(lifecycleScope)
    }
}