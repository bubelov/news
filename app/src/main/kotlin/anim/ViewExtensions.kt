package anim

import android.view.View
import android.view.animation.AccelerateInterpolator

fun View.showSmooth() = animateAlpha(1f)

fun View.hideSmooth() = animateAlpha(0f)

private fun View.animateAlpha(alpha: Float) {
    animate()
        .alpha(alpha)
        .setDuration(200)
        .interpolator = AccelerateInterpolator()
}