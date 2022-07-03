package navigation

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import dialog.showErrorDialog

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