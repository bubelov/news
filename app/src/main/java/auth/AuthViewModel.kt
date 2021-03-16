package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.PreferencesRepository

class AuthViewModel(
    private val newsApiSwitcher: NewsApiSwitcher,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    suspend fun getAuthType() = preferencesRepository.get().authType

    suspend fun setAuthType(newAuthType: String) {
        preferencesRepository.save { authType = newAuthType }
        newsApiSwitcher.switch(newAuthType)
    }

    suspend fun setSyncOnStartup(syncOnStartup: Boolean) {
        preferencesRepository.save { this.syncOnStartup = syncOnStartup }
    }
}