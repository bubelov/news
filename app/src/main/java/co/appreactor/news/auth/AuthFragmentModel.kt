package co.appreactor.news.auth

import androidx.lifecycle.ViewModel
import co.appreactor.news.common.Preferences
import co.appreactor.news.common.getAuthType
import co.appreactor.news.common.setAuthType
import kotlinx.coroutines.flow.first

class AuthFragmentModel(
    private val prefs: Preferences
) : ViewModel() {

    suspend fun getAuthType() = prefs.getAuthType().first()

    suspend fun setAuthType(authType: String) = prefs.setAuthType(authType)
}