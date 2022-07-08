package dialog

import android.content.DialogInterface
import android.util.Log
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Fragment.showDialog(
    @StringRes titleId: Int,
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    showDialog(
        title = getString(titleId),
        message = message,
        onDismissListener = onDismissListener,
    )
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
    Log.e(javaClass.simpleName, t.message, t)
    val message = t.message ?: getString(R.string.got_exception_of_class_s, t.javaClass.simpleName)

    showDialog(R.string.error, message) {
        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.showErrorDialog(
    message: String,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    showDialog(R.string.error, message) {
        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}