package anim

import android.view.View

fun animateVisibilityChanges(
    views: List<View>,
    visibleViews: List<View>,
) {
    views.forEach {
        if (visibleViews.contains(it)) {
            it.showSmooth()
        } else {
            it.hideSmooth()
        }
    }
}