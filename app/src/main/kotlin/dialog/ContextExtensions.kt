package dialog

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import androidx.annotation.StringRes
import co.appreactor.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showDialog(
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

fun Context.showDialog(
    title: String,
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
}

fun Context.showErrorDialog(
    t: Throwable,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    Log.e(javaClass.simpleName, t.message, t)
    val message = t.message ?: getString(R.string.got_exception_of_class_s, t.javaClass.simpleName)
    showDialog(R.string.error, message, onDismissListener)
}

fun Context.showErrorDialog(
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    showDialog(R.string.error, message, onDismissListener)
}