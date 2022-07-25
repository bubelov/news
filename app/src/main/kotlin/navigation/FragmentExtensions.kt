package navigation

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import dialog.showErrorDialog

fun Fragment.showKeyboard(view: View) {
    val inputManager = requireContext().getSystemService<InputMethodManager>()!!
    inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun Fragment.hideKeyboard(view: View) {
    val inputManager = requireContext().getSystemService<InputMethodManager>()!!
    inputManager.hideSoftInputFromWindow(view.windowToken, 0)
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