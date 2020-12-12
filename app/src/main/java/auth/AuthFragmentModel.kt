package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.Preferences
import common.getAuthType
import common.setAuthType
import kotlinx.coroutines.flow.first

class AuthFragmentModel(
    private val newsApiSwitcher: NewsApiSwitcher,
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun getAuthType() = prefs.getAuthType().first()

    suspend fun setAuthType(authType: String) {
        prefs.setAuthType(authType)
        newsApiSwitcher.switch(authType)
    }
}