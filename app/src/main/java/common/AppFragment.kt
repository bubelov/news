package common

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment

abstract class AppFragment(
    private val showToolbar: Boolean = true,
    private val drawerLockMode: Int = DrawerLayout.LOCK_MODE_UNLOCKED,
) : Fragment() {

    protected val toolbar by lazy { activity.binding.toolbar }

    protected val searchPanel by lazy { activity.binding.searchPanel }

    protected val searchPanelText by lazy { activity.binding.searchPanelText }

    protected val searchPanelClearButton by lazy { activity.binding.searchPanelClearButton }

    private val activity by lazy { requireActivity() as AppActivity }

    private val appBarLayout by lazy { activity.binding.appBarLayout }

    private val drawer by lazy { activity.binding.drawerLayout }

    private val drawerToggle by lazy { activity.drawerToggle }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drawer.setDrawerLockMode(drawerLockMode)

        appBarLayout.isVisible = showToolbar

        toolbar.apply {
            setNavigationOnClickListener { drawer.open() }
            drawerToggle.syncState()
            title = ""
            menu?.clear()
        }
    }
}