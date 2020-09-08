package co.appreactor.nextcloud.news.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.common.Preferences
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first

class AuthFragmentModel(
    private val prefs: Preferences,
    private val context: Context
) : ViewModel() {

    suspend fun isLoggedIn(): Boolean {
        if (prefs.getString(Preferences.SERVER_URL).first().isNotBlank()) {
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