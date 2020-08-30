package co.appreactor.nextcloud.news

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first

class AuthFragmentModel(
    private val prefs: PreferencesRepository,
    private val context: Context
) : ViewModel() {

    suspend fun isLoggedIn(): Boolean {
        if (prefs.get(PreferencesRepository.SERVER_URL).first().isNotBlank()) {
            return true
        }

        return try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            true
        } catch (e: SSOException) {
            false
        }
    }
}