package navigation

import androidx.fragment.app.Fragment

fun Fragment.openUrl(
    url: String,
    useBuiltInBrowser: Boolean,
) {
    requireContext().openUrl(
        url = url,
        useBuiltInBrowser = useBuiltInBrowser,
    )
}