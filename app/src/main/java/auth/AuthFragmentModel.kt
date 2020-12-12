package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.Preferences
import common.Preferences.Companion.AUTH_TYPE

class AuthFragmentModel(
    private val newsApiSwitcher: NewsApiSwitcher,
    private val prefs: Preferences,
) : ViewModel() {

    var authType
        get() = prefs.getStringBlocking(AUTH_TYPE)
        set(value) {
            prefs.putStringBlocking(AUTH_TYPE, value)
            newsApiSwitcher.switch(value)
        }
}