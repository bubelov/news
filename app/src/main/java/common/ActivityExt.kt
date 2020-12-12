package common

import android.app.Activity
import android.view.WindowManager

fun Activity.hideKeyboard() {
    window.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    )
}