package co.appreactor.nextcloud.news.common

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Fragment.showDialog(@StringRes titleId: Int, @StringRes messageId: Int) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titleId)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

fun Fragment.showDialog(@StringRes titleId: Int, message: String) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titleId)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

fun Fragment.showDialog(title: String, message: String) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}