package org.vestifeed.navigation

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feeds.FeedsFragment
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.databinding.ActivityBinding

class Activity : AppCompatActivity() {

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

        db().confQueries.update { it.copy(syncedOnStartup = false) }

        lifecycleScope.launch {
            val conf = db().confQueries.select()

            if (conf.backend.isNotBlank()) {
                supportFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        bundleOf("filter" to EntriesFilter.Unread),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()



        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        binding.bottomNav.apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.newsFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                EntriesFragment::class.java,
                                bundleOf("filter" to EntriesFilter.Unread),
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
