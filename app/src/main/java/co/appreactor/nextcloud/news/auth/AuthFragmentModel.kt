package co.appreactor.nextcloud.news.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.common.Preferences
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber

class AuthFragmentModel(
    private val prefs: Preferences
) : ViewModel() {

    suspend fun isLoggedIn(context: Context): Boolean {
        if (prefs.getString(Preferences.SERVER_URL).first().isNotBlank()) {
            return true
        }

        return try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            true
        } catch (e: Exception) {
            if (e !is NoCurrentAccountSelectedException) {
                Timber.e(e)
            }

            false
        }
    }
}