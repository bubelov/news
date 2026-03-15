package navigation

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import auth.AuthFragment
import auth.MinifluxAuthFragment
import auth.NextcloudAuthFragment
import enclosures.EnclosuresFragment
import entries.EntriesFragment
import feedsettings.FeedSettingsFragment
import feeds.FeedsFragment
import co.appreactor.news.R
import entry.EntryFragment
import search.SearchFragment
import settings.SettingsFragment

class NavController private constructor(private val fragmentManager: FragmentManager, private val containerViewId: Int) {

    private val backStack = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())

    private var onDestinationChangedListener: ((Int, Bundle?) -> Unit)? = null

    init {
        sharedNavController = this
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun create(fragmentManager: FragmentManager, containerViewId: Int): NavController {
            // This will use the init block to set sharedNavController
            return NavController(fragmentManager, containerViewId)
        }
    }

    fun addOnDestinationChangedListener(listener: (Int, Bundle?) -> Unit) {
        onDestinationChangedListener = listener
    }

    fun navigate(destinationId: Int, args: Bundle? = null, navOptions: NavOptions? = null) {
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
                    fragmentManager.popBackStackImmediate(popUpToTag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
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

        handler.post {
            android.util.Log.d("NavController", "Notifying listener: destination=$destinationId")
            onDestinationChangedListener?.invoke(destinationId, args)
        }
    }

    fun navigate(resId: Int, args: Bundle?) {
        navigate(resId, args, null)
    }

    fun popBackStack(): Boolean {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
            if (backStack.isNotEmpty()) {
                backStack.removeAt(backStack.size - 1)
            }
            return true
        }
        return false
    }

    fun popBackStack(destinationId: Int, inclusive: Boolean) {
        val tag = destinationId.toString()
        fragmentManager.popBackStack(tag, if (inclusive) FragmentManager.POP_BACK_STACK_INCLUSIVE else 0)
    }

    fun getCurrentDestinationId(): Int? {
        return if (backStack.isNotEmpty()) backStack.last() else null
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

        fun setEnterAnim(anim: Int): Builder {
            navOptions.enterAnim = anim
            return this
        }

        fun setExitAnim(anim: Int): Builder {
            navOptions.exitAnim = anim
            return this
        }

        fun setPopEnterAnim(anim: Int): Builder {
            navOptions.popEnterAnim = anim
            return this
        }

        fun setPopExitAnim(anim: Int): Builder {
            navOptions.popExitAnim = anim
            return this
        }

        fun setPopUpTo(destinationId: Int, inclusive: Boolean): Builder {
            navOptions.popUpTo = destinationId
            navOptions.popUpToInclusive = inclusive
            return this
        }

        fun build(): NavOptions = navOptions
    }
}

fun Fragment.findNavController(containerViewId: Int): NavController {
    val activity = requireActivity()
    val fragmentManager = activity.supportFragmentManager
    return NavController.create(fragmentManager, containerViewId)
}

private var sharedNavController: NavController? = null

fun Fragment.findNavController(): NavController {
    val activity = requireActivity()
    val containerView = activity.findViewById<android.view.View>(R.id.navHost)
    val containerViewId = containerView?.id ?: throw IllegalStateException("navHost not found")
    val fragmentManager = activity.supportFragmentManager
    
    if (sharedNavController == null) {
        sharedNavController = NavController.create(fragmentManager, containerViewId)
    }
    return sharedNavController!!
}

fun getSharedNavController(): NavController? = sharedNavController

inline fun <reified T : Any> Fragment.navArgs(): Lazy<T> {
    return lazy { 
        val args = requireArguments()
        when (T::class.java) {
            AuthFragmentArgs::class.java -> AuthFragmentArgs.fromBundle(args) as T
            MinifluxAuthFragmentArgs::class.java -> MinifluxAuthFragmentArgs.fromBundle(args) as T
            NextcloudAuthFragmentArgs::class.java -> NextcloudAuthFragmentArgs.fromBundle(args) as T
            EntriesFragmentArgs::class.java -> EntriesFragmentArgs.fromBundle(args) as T
            FeedsFragmentArgs::class.java -> FeedsFragmentArgs.fromBundle(args) as T
            EntryFragmentArgs::class.java -> EntryFragmentArgs.fromBundle(args) as T
            SearchFragmentArgs::class.java -> SearchFragmentArgs.fromBundle(args) as T
            FeedSettingsFragmentArgs::class.java -> FeedSettingsFragmentArgs.fromBundle(args) as T
            SettingsFragmentArgs::class.java -> SettingsFragmentArgs.fromBundle(args) as T
            EnclosuresFragmentArgs::class.java -> EnclosuresFragmentArgs.fromBundle(args) as T
            else -> throw IllegalArgumentException("Unknown argument class: ${T::class.java}")
        }
    }
}
