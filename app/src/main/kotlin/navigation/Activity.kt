package navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.fragment.app.replace
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

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

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

        Di.get(ConfRepo::class.java).update { it.copy(syncedOnStartup = false) }
        lifecycleScope.launch { Di.get(OpenGraphImagesRepo::class.java).fetchEntryImages() }
    }

    override fun onStart() {
        super.onStart()

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
            } else {
//                supportFragmentManager.commit {
//                    setReorderingAllowed(true)
//                    replace<AuthFragment>(R.id.fragmentContainerView)
//                    addToBackStack(null)
//                }
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
