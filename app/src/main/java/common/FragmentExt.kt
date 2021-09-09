package common

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

fun Fragment.app() = requireContext().applicationContext as App

fun Fragment.showDialog(
    @StringRes titleId: Int,
    @StringRes messageId: Int,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titleId)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
}

fun Fragment.showDialog(
    @StringRes titleId: Int,
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titleId)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
}

fun Fragment.showDialog(
    title: String,
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
}

fun Fragment.showErrorDialog(
    t: Throwable,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    Timber.e(t)

    val message = t.message ?: getString(R.string.got_exception_of_class_s, t.javaClass.simpleName)

    showDialog(R.string.error, message) {
        lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.showErrorDialog(
    message: String,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    showDialog(R.string.error, message) {
        lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.openLink(
    link: String
) {
    try {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(link)
            )
        )
    } catch (e: ActivityNotFoundException) {
        showErrorDialog(getString(R.string.invalid_url_s, link))
    } catch (e: Exception) {
        showErrorDialog(e)
    }
}

private fun Fragment.activity() = requireActivity() as AppActivity