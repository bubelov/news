package common

import android.os.Bundle
import android.os.PersistableBundle
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityAppBinding
import enclosures.AudioEnclosuresRepository
import entries.EntriesFilter
import entriesimages.EntriesImagesRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class Activity : AppCompatActivity() {

    private val model: ActivityModel by viewModel()

    lateinit var binding: ActivityAppBinding

    private val navController by lazy { findNavController(R.id.nav_host_fragment) }

    lateinit var drawerToggle: ActionBarDrawerToggle

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        syncDrawerState(destination)
        binding.appBarLayout.isVisible = destination.id != R.id.authFragment

        binding.bottomNavigation.isVisible =
            destination.id != R.id.authFragment
                    && destination.id != R.id.minifluxAuthFragment
                    && destination.id != R.id.nextcloudAuthFragment
                    && destination.id != R.id.feedEntriesFragment

        when (destination.id) {
            R.id.newsFragment -> args!!.putParcelable("filter", EntriesFilter.NotBookmarked)
            R.id.bookmarksFragment -> args!!.putParcelable("filter", EntriesFilter.Bookmarked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.open,
            R.string.close,
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)

        initDrawerHeader()

        lifecycleScope.launchWhenCreated {
            get<AudioEnclosuresRepository>().apply {
                deleteIncompleteDownloads()
            }
        }

        lifecycleScope.launchWhenCreated {
            get<EntriesImagesRepository>().syncOpenGraphImages()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        drawerToggle.syncState()
    }

    override fun onStart() {
        super.onStart()
        binding.navigationView.setupWithNavController(navController)
        binding.bottomNavigation.setupWithNavController(navController)
        scrollToTopOnSecondClick()
        navController.addOnDestinationChangedListener(navListener)
    }

    private fun initDrawerHeader() {
        val headerView = binding.navigationView.getHeaderView(0)!!
        val titleView = headerView.findViewById<TextView>(R.id.title)!!
        val subtitleView = headerView.findViewById<TextView>(R.id.subtitle)!!

        combine(model.accountTitle(), model.accountSubtitle()) { title, subtitle ->
            titleView.text = title
            subtitleView.isVisible = subtitle.isNotBlank()
            subtitleView.text = subtitle
        }.launchIn(lifecycleScope)
    }

    private fun scrollToTopOnSecondClick() {
        binding.bottomNavigation.setOnItemReselectedListener {
            supportFragmentManager.fragments.forEach { fragment ->
                if (fragment is NavHostFragment) {
                    fragment.childFragmentManager.fragments.forEach { childFragment ->
                        if (childFragment is Scrollable) {
                            childFragment.scrollToTop()
                        }
                    }
                }
            }
        }
    }

    private fun syncDrawerState(destination: NavDestination) {
        val lockDrawer = when (destination.id) {
            R.id.authFragment -> true
            R.id.minifluxAuthFragment -> true
            R.id.nextcloudAuthFragment -> true
            R.id.feedEntriesFragment -> true
            R.id.settingsFragment -> true
            else -> false
        }

        val lockMode = if (lockDrawer) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
        binding.drawerLayout.setDrawerLockMode(lockMode)
    }
}