package common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R

fun Fragment.app() = requireContext().applicationContext as App

fun Fragment.showErrorDialog(
    t: Throwable,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    val message = t.message ?: getString(R.string.got_exception_of_class_s, t.javaClass.simpleName)

    requireContext().showDialog(R.string.error, message) {
        lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.showErrorDialog(
    message: String,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    requireContext().showDialog(R.string.error, message) {
        lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.openUrl(
    url: String,
    useBuiltInBrowser: Boolean,
) {
    val uri = runCatching {
        Uri.parse(url)
    }.getOrElse {
        showErrorDialog(it)
        return
    }

    try {
        if (useBuiltInBrowser) {
            CustomTabsIntent.Builder().build().launchUrl(requireContext(), uri)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (e: Exception) {
        showErrorDialog(e)
    }
}

fun Fragment.screenWidth(): Int {
    return when {
        Build.VERSION.SDK_INT >= 31 -> {
            val windowManager = requireContext().getSystemService<WindowManager>()!!
            windowManager.currentWindowMetrics.bounds.width()
        }
        Build.VERSION.SDK_INT >= 30 -> {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            requireContext().display?.getRealMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
        else -> {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }
}

fun Fragment.openCachedPodcast(cacheUri: Uri?, enclosureLinkType: String) {
    if (cacheUri == null) {
        showErrorDialog(Exception("Can't find podcast audio file"))
        return
    }

    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = cacheUri
        setDataAndType(cacheUri, enclosureLinkType)
    }

    runCatching {
        startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            requireContext().showDialog(
                R.string.error,
                R.string.you_have_no_apps_which_can_play_this_podcast
            )
        } else {
            showErrorDialog(it)
        }
    }
}