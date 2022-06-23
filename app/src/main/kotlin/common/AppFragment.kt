package common

import android.os.Bundle
import android.view.View
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar

abstract class AppFragment : Fragment() {

    protected val searchPanel by lazy { activity.binding.searchPanel }

    protected val searchPanelText by lazy { activity.binding.searchPanelText }

    protected val searchPanelClearButton by lazy { activity.binding.searchPanelClearButton }

    private val activity by lazy { requireActivity() as Activity }

    private val drawer by lazy {
        if (getActivity() is Activity) {
            activity.binding.drawerLayout
        } else {
            null
        }
    }

    private val drawerToggle by lazy { activity.drawerToggle }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedToolbar()?.apply {
            setNavigationOnClickListener { drawer?.open() }
            drawerToggle.syncState()
            title = ""
            menu?.clear()
        }
    }

    protected fun MaterialToolbar.setupUpNavigation(hideKeyboard: Boolean = false) {
        navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
        setNavigationOnClickListener {
            if (hideKeyboard) requireContext().hideKeyboard(searchPanelText)
            findNavController().popBackStack()
        }
    }
}