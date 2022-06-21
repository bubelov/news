package common

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
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

    val model: AppViewModel by viewModel()

    lateinit var binding: ActivityAppBinding

    private val navController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    lateinit var drawerToggle: ActionBarDrawerToggle

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        binding.appBarLayout.isVisible = destination.id != R.id.authFragment
        binding.bottomNavigation.isVisible =
            destination.id != R.id.authFragment && destination.id != R.id.feedEntriesFragment

        val navView = binding.navigationView

        when (destination.id) {
            R.id.newsFragment -> {
                navView.setCheckedItem(R.id.news)
                args!!.putParcelable("filter", EntriesFilter.NotBookmarked)
            }
            R.id.bookmarksFragment -> {
                navView.setCheckedItem(R.id.bookmarks)
                args!!.putParcelable("filter", EntriesFilter.Bookmarked)
            }
            R.id.feedsFragment -> {
                navView.setCheckedItem(R.id.feeds)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initNavigationView()

        lifecycleScope.launchWhenCreated {
            get<AudioEnclosuresRepository>().apply {
                deleteIncompleteDownloads()
            }
        }

        lifecycleScope.launchWhenCreated {
            get<EntriesImagesRepository>().syncOpenGraphImages()
        }

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.open,
            R.string.close,
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)
    }

    override fun onStart() {
        super.onStart()
        binding.bottomNavigation.setupWithNavController(navController)
        scrollToTopOnSecondClick()
        navController.addOnDestinationChangedListener(navListener)
    }

    private fun initNavigationView() {
        val headerView = binding.navigationView.getHeaderView(0)!!
        val titleView = headerView.findViewById<TextView>(R.id.title)!!
        val subtitleView = headerView.findViewById<TextView>(R.id.subtitle)!!

        combine(model.accountTitle(), model.accountSubtitle()) { title, subtitle ->
            titleView.text = title
            subtitleView.isVisible = subtitle.isNotBlank()
            subtitleView.text = subtitle
        }.launchIn(lifecycleScope)

        binding.navigationView.setNavigationItemSelectedListener {
            binding.drawerLayout.close()

            if (it.isChecked) {
                return@setNavigationItemSelectedListener false
            } else {
                it.isChecked = it.groupId == R.id.main
            }

            when (it.itemId) {
                R.id.news -> {
                    navController.navigate(
                        R.id.newsFragment,
                        bundleOf(Pair("filter", EntriesFilter.NotBookmarked))
                    )
                }

                R.id.bookmarks -> {
                    navController.navigate(
                        R.id.bookmarksFragment,
                        bundleOf(Pair("filter", EntriesFilter.Bookmarked))
                    )
                }

                R.id.feeds -> {
                    navController.navigate(R.id.feedsFragment)
                }

                R.id.settings -> {
                    navController.navigate(R.id.settingsFragment)
                }
            }

            return@setNavigationItemSelectedListener true
        }
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
}