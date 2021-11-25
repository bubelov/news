package common

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import com.google.android.material.appbar.MaterialToolbar
import timber.log.Timber

abstract class AppFragment(
    private val showToolbar: Boolean = true,
    private val lockDrawer: Boolean = true,
) : Fragment() {

    protected val toolbar by lazy { activity.binding.toolbar }

    protected val searchPanel by lazy { activity.binding.searchPanel }

    protected val searchPanelText by lazy { activity.binding.searchPanelText }

    protected val searchPanelClearButton by lazy { activity.binding.searchPanelClearButton }

    protected var isDrawerLocked: Boolean
        get() = drawer.getDrawerLockMode(drawer) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        set(value) = drawer.setDrawerLockMode(if (value) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)

    private val activity by lazy { requireActivity() as AppActivity }

    private val appBarLayout by lazy { activity.binding.appBarLayout }

    private val drawer by lazy { activity.binding.drawerLayout }

    private val drawerToggle by lazy { activity.drawerToggle }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isDrawerLocked = lockDrawer

        appBarLayout.isVisible = showToolbar

        toolbar.apply {
            setNavigationOnClickListener { drawer.open() }
            drawerToggle.syncState()
            title = ""
            menu?.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("Resuming fragment (class = ${javaClass.simpleName})")
    }

    protected fun MaterialToolbar.setupUpNavigation(hideKeyboard: Boolean = false) {
        setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        setNavigationOnClickListener {
            if (hideKeyboard) requireContext().hideKeyboard(searchPanelText)
            findNavController().popBackStack()
        }
    }
}