package anim

import android.view.View
import android.view.animation.AccelerateInterpolator

fun View.showSmooth() {
    animate()
        .alpha(1f)
        .interpolator = AccelerateInterpolator()
}

fun View.hideSmooth() {
    animate()
        .alpha(0f)
        .interpolator = AccelerateInterpolator()
}