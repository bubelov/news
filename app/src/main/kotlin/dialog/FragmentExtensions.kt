package dialog

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.appreactor.news.R

fun Fragment.showErrorDialog(
    t: Throwable,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    Log.e(javaClass.simpleName, t.message, t)
    val message = t.message ?: getString(R.string.got_exception_of_class_s, t.javaClass.simpleName)

    requireContext().showDialog(R.string.error, message) {
        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}

fun Fragment.showErrorDialog(
    message: String,
    onDismissListener: (suspend () -> Unit)? = null,
) {
    requireContext().showDialog(R.string.error, message) {
        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            onDismissListener?.invoke()
        }
    }
}