package org.vestifeed.navigation

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feeds.FeedsFragment

abstract class AppFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as Activity).binding.bottomNav.isVisible =
            parentFragmentManager.backStackEntryCount == 0 &&
                    (this is EntriesFragment || this is FeedsFragment)
    }
}