package dialog

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Fragment.showErrorDialog(
    t: Throwable,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    requireContext().showErrorDialog(
        t = t,
        onDismissListener = onDismissListener,
    )
}

fun Fragment.showErrorDialog(
    @StringRes message: Int,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    requireContext().showErrorDialog(
        message = message,
        onDismissListener = onDismissListener,
    )
}

fun Fragment.showErrorDialog(
    message: String,
    onDismissListener: DialogInterface.OnDismissListener? = null,
) {
    requireContext().showErrorDialog(
        message = message,
        onDismissListener = onDismissListener,
    )
}