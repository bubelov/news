package common

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showKeyboard() {
    val inputManager = getSystemService<InputMethodManager>()!!
    @Suppress("DEPRECATION")
    inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

fun Context.hideKeyboard(view: View) {
    val inputManager = getSystemService<InputMethodManager>()!!
    inputManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Context.showDialog(
    @StringRes titleId: Int,
    @StringRes messageId: Int,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleId)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(onDismissListener)
        .show()
}

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