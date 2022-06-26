package common

import android.os.Bundle
import android.view.View
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedToolbar()?.apply {
            title = ""
            menu?.clear()
        }
    }

    protected fun MaterialToolbar.setupGlobalNavigation() {
        (requireActivity() as? Activity)?.apply {
            setNavigationOnClickListener { binding.drawerLayout.open() }
            drawerToggle.syncState()
        }
    }

    protected fun MaterialToolbar.setupUpNavigation() {
        navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
        setNavigationOnClickListener { findNavController().popBackStack() }
    }
}