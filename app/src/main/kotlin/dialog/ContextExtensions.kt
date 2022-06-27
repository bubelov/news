package dialog

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showDialog(
    @StringRes titleId: Int,
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleId)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
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