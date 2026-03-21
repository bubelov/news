package org.vestifeed.navigation

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.vestifeed.R
import org.vestifeed.auth.AuthFragment
import org.vestifeed.auth.MinifluxAuthFragment
import org.vestifeed.auth.NextcloudAuthFragment
import org.vestifeed.enclosures.EnclosuresFragment
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feedsettings.FeedSettingsFragment
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.entry.EntryFragment
import org.vestifeed.search.SearchFragment
import org.vestifeed.settings.SettingsFragment

class NavController private constructor(
    private val fragmentManager: FragmentManager,
    private val containerViewId: Int
) {

    private val backStack = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())

    private var onDestinationChangedListener: ((Int, Bundle?) -> Unit)? = null

    init {
        sharedNavController = this
        syncBackStackWithFragmentManager()
    }

    private fun syncBackStackWithFragmentManager() {
        backStack.clear()
        for (i in 0 until fragmentManager.backStackEntryCount) {
            val entry = fragmentManager.getBackStackEntryAt(i)
            entry.name?.toIntOrNull()?.let { backStack.add(it) }
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun create(fragmentManager: FragmentManager, containerViewId: Int): NavController {
            // This will use the init block to set sharedNavController
            return NavController(fragmentManager, containerViewId)
        }
    }

    fun navigate(destinationId: Int, args: Bundle? = null, navOptions: NavOptions? = null) {
        android.util.Log.d(
            "NavController",
            "navigate: destinationId=$destinationId, backStack before=$backStack"
        )

        val transaction = fragmentManager.beginTransaction()

        navOptions?.let { options ->
            if (options.enterAnim != 0) {
                transaction.setCustomAnimations(
                    options.enterAnim,
                    options.exitAnim,
                    options.popEnterAnim,
                    options.popExitAnim,
                )
            }

            if (options.popUpTo != 0) {
                val popUpToTag = options.popUpTo.toString()
                for (i in fragmentManager.backStackEntryCount - 1 downTo 0) {
                    val entry = fragmentManager.getBackStackEntryAt(i)
                    if (entry.name == popUpToTag) {
                        break
                    }
                    fragmentManager.popBackStackImmediate()
                }
                if (options.popUpToInclusive) {
                    fragmentManager.popBackStackImmediate(
                        popUpToTag,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                }
            }
        }

        val fragment = createFragment(destinationId)
        fragment.arguments = args ?: Bundle()

        transaction.replace(containerViewId, fragment, destinationId.toString())

        if (navOptions?.popUpTo == 0 || navOptions == null) {
            transaction.addToBackStack(destinationId.toString())
            backStack.add(destinationId)
        }

        transaction.commit()

        android.util.Log.d(
            "NavController",
            "navigate: backStack after=$backStack, will notify $destinationId"
        )
        handler.post {
            android.util.Log.d("NavController", "Notifying listener: destination=$destinationId")
            onDestinationChangedListener?.invoke(destinationId, args)
        }
    }

    fun popBackStack(): Boolean {
        android.util.Log.d("NavController", "XXXX popBackStack called")
        android.util.Log.d(
            "NavController",
            "popBackStack: backStack before=$backStack, fragmentManager backStack count=${fragmentManager.backStackEntryCount}"
        )
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
            if (backStack.isNotEmpty()) {
                backStack.removeAt(backStack.size - 1)
            }

            val currentDestinationId =
                if (backStack.isNotEmpty()) backStack.last() else containerViewId
            android.util.Log.d(
                "NavController",
                "popBackStack: backStack after=$backStack, currentDestinationId=$currentDestinationId"
            )
            handler.post {
                android.util.Log.d(
                    "NavController",
                    "popBackStack: notifying listener with destination=$currentDestinationId"
                )
                onDestinationChangedListener?.invoke(currentDestinationId, null)
            }
            return true
        }
        return false
    }

    fun popBackStack(destinationId: Int, inclusive: Boolean) {
        val tag = destinationId.toString()
        fragmentManager.popBackStack(
            tag,
            if (inclusive) FragmentManager.POP_BACK_STACK_INCLUSIVE else 0
        )
    }

    private fun createFragment(destinationId: Int): Fragment {
        return when (destinationId) {
            R.id.authFragment -> AuthFragment()
            R.id.minifluxAuthFragment -> MinifluxAuthFragment()
            R.id.nextcloudAuthFragment -> NextcloudAuthFragment()
            R.id.entriesFragment -> EntriesFragment()
            R.id.newsFragment -> EntriesFragment()
            R.id.bookmarksFragment -> EntriesFragment()
            R.id.feedsFragment -> FeedsFragment()
            R.id.entryFragment -> EntryFragment()
            R.id.searchFragment -> SearchFragment()
            R.id.feedEntriesFragment -> EntriesFragment()
            R.id.feedSettingsFragment -> FeedSettingsFragment()
            R.id.settingsFragment -> SettingsFragment()
            R.id.enclosuresFragment -> EnclosuresFragment()
            else -> throw IllegalArgumentException("Unknown destination id: $destinationId")
        }
    }
}

class NavOptions {
    var enterAnim: Int = 0
    var exitAnim: Int = 0
    var popEnterAnim: Int = 0
    var popExitAnim: Int = 0
    var popUpTo: Int = 0
    var popUpToInclusive: Boolean = false

    companion object {
        fun Builder(): NavOptions.Builder = NavOptions.Builder()
    }

    class Builder {
        private val navOptions = NavOptions()

        fun setPopUpTo(destinationId: Int, inclusive: Boolean): Builder {
            navOptions.popUpTo = destinationId
            navOptions.popUpToInclusive = inclusive
            return this
        }

        fun build(): NavOptions = navOptions
    }
}

private var sharedNavController: NavController? = null

fun Fragment.findNavController(): NavController {
    val activity = requireActivity()
    val containerView = activity.findViewById<android.view.View>(R.id.fragmentContainerView)
    val containerViewId = containerView?.id ?: throw IllegalStateException("navHost not found")
    val fragmentManager = activity.supportFragmentManager

    if (sharedNavController == null) {
        sharedNavController = NavController.create(fragmentManager, containerViewId)
    }
    return sharedNavController!!
}

inline fun <reified T : Any> Fragment.navArgs(): Lazy<T> {
    return lazy {
        val args = requireArguments()
        when (T::class.java) {
            EntryFragmentArgs::class.java -> EntryFragmentArgs.fromBundle(args) as T
            SearchFragmentArgs::class.java -> SearchFragmentArgs.fromBundle(args) as T
            FeedSettingsFragmentArgs::class.java -> FeedSettingsFragmentArgs.fromBundle(args) as T
            SettingsFragmentArgs::class.java -> SettingsFragmentArgs.fromBundle(args) as T
            EnclosuresFragmentArgs::class.java -> EnclosuresFragmentArgs.fromBundle(args) as T
            else -> throw IllegalArgumentException("Unknown argument class: ${T::class.java}")
        }
    }
}
