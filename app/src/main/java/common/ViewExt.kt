package common

import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.view.isVisible

fun View.show(animate: Boolean = false) {
    isVisible = true

    if (animate) {
        alpha = 0f

        this.animate().apply {
            alpha(1f)
            duration = 300
            interpolator = AccelerateInterpolator()
        }
    }
}

fun View.hide() {
    isVisible = false
}