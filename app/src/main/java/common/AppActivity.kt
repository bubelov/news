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
import entries.EntriesFilter
import podcasts.PodcastsRepository
import entriesimages.EntriesImagesRepository
import kotlinx.coroutines.flow.collect
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

class AppActivity : AppCompatActivity() {

    val model: AppViewModel by viewModel()

    lateinit var binding: ActivityAppBinding

    private val navController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    lateinit var drawerToggle: ActionBarDrawerToggle

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        if (destination.id == R.id.entriesFragment) {
            val filter = args?.getParcelable<EntriesFilter>("filter")
            binding.bottomNavigation.isVisible = filter !is EntriesFilter.BelongToFeed
            return@OnDestinationChangedListener
        }

        if (destination.id == R.id.bookmarksFragment) {
            args!!.putParcelable("filter", EntriesFilter.Bookmarked)
        }

        val bottomNavigationIsVisible =
            destination.id == R.id.feedsFragment
                    || destination.id == R.id.bookmarksFragment
                    || destination.id == R.id.podcastsFragment
                    || destination.id == R.id.entriesFragment

        binding.bottomNavigation.isVisible = bottomNavigationIsVisible
    }

    init {
        initNavigationView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launchWhenCreated {
            get<PodcastsRepository>().apply {
                deleteIncompleteDownloads()
            }
        }

        lifecycleScope.launchWhenCreated {
            get<EntriesImagesRepository>().syncPreviews()
        }

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.open,
            R.string.close,
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)

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
                        R.id.entriesFragment,
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
                    navController.navigate(R.id.action_global_to_settingsFragment)
                }
            }

            return@setNavigationItemSelectedListener true
        }
    }

    override fun onStart() {
        super.onStart()
        binding.bottomNavigation.setupWithNavController(navController)

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

        binding.bottomNavigation.setOnItemSelectedListener {
            val navigationView = binding.navigationView

            when (it.itemId) {
                R.id.entriesFragment -> {
                    navigationView.setCheckedItem(R.id.news)

                    navController.navigate(
                        R.id.entriesFragment,
                        bundleOf(Pair("filter", EntriesFilter.NotBookmarked))
                    )
                }

                R.id.bookmarksFragment -> {
                    navigationView.setCheckedItem(R.id.bookmarks)

                    navController.navigate(
                        R.id.bookmarksFragment,
                        bundleOf(Pair("filter", EntriesFilter.Bookmarked))
                    )
                }

                R.id.feedsFragment -> {
                    navigationView.setCheckedItem(R.id.feeds)
                    navController.navigate(R.id.feedsFragment)
                }

                R.id.settingsFragment -> {
                    navController.navigate(R.id.settingsFragment)
                }
            }

            false
        }

        navController.addOnDestinationChangedListener(navListener)
    }

    private fun initNavigationView() {
        lifecycleScope.launchWhenResumed {
            val headerView = binding.navigationView.getHeaderView(0) ?: throw Exception()
            val titleView = headerView.findViewById<TextView>(R.id.title) ?: throw Exception()
            val subtitleView = headerView.findViewById<TextView>(R.id.subtitle) ?: throw Exception()

            model.account().collect {
                titleView.text = it.title
                subtitleView.isVisible = it.subtitle.isNotBlank()
                subtitleView.text = it.subtitle
            }
        }
    }
}