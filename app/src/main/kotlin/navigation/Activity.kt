package navigation

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import conf.ConfRepo
import di.Di
import entries.EntriesFilter
import entries.EntriesFragment
import feeds.FeedsFragment
import kotlinx.coroutines.launch
import opengraph.OpenGraphImagesRepo

class Activity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    lateinit var binding: ActivityBinding

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    override fun onBackStackChanged() {
        updateBottomNavVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                v.updatePadding(bottom = it.bottom)
            }
            insets
        }

        Di.get(ConfRepo::class.java).update { it.copy(syncedOnStartup = false) }
        lifecycleScope.launch { Di.get(OpenGraphImagesRepo::class.java).fetchEntryImages() }
    }

    override fun onStart() {
        super.onStart()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        supportFragmentManager.addOnBackStackChangedListener(this)

        lifecycleScope.launch {
            val confRepo = Di.get(ConfRepo::class.java)
            val config = confRepo.conf.value

            if (config.backend.isNotBlank()) {
                supportFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        bundleOf("filter" to EntriesFilter.NotBookmarked),
                    )
                }

                binding.bottomNav.isVisible = true
            }
        }

        binding.bottomNav.apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.newsFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                EntriesFragment::class.java,
                                bundleOf("filter" to EntriesFilter.NotBookmarked),
                            )
                        }
                        true
                    }

                    R.id.bookmarksFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                EntriesFragment::class.java,
                                bundleOf("filter" to EntriesFilter.Bookmarked),
                            )
                        }
                        true
                    }

                    R.id.feedsFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                FeedsFragment::class.java,
                                bundleOf("url" to ""),
                            )
                        }
                        true
                    }

                    else -> false
                }
            }

            setOnItemReselectedListener(createOnItemReselectedListener())
        }
    }

    override fun onStop() {
        super.onStop()
        supportFragmentManager.removeOnBackStackChangedListener(this)
    }

    private fun updateBottomNavVisibility() {
        val hasNoBackStack = supportFragmentManager.backStackEntryCount == 0
        val currentFragment = supportFragmentManager.fragments.firstOrNull()
        val isEntriesOrFeedsFragment =
            currentFragment is EntriesFragment || currentFragment is FeedsFragment

        binding.bottomNav.isVisible = hasNoBackStack && isEntriesOrFeedsFragment
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
