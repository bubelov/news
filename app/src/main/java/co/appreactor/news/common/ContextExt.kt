package co.appreactor.news.common

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import co.appreactor.news.R
import com.google.android.material.elevation.ElevationOverlayProvider

fun Context.inDarkMode(): Boolean {
    return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

fun Context.getSurfaceColor(elevation: Float): Int {
    return ElevationOverlayProvider(this).compositeOverlay(getColorFromAttr(R.attr.colorSurface), elevation)
}

@ColorInt
fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrColor, typedValue, true)
    return typedValue.data
}

fun Context.showKeyboard() {
    val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}