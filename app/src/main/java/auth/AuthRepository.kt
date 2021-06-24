package auth

import android.content.Context
import android.content.res.Resources
import co.appreactor.news.R
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import common.Preferences
import common.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class AuthRepository(
    private val prefs: PreferencesRepository,
    private val resources: Resources,
    private val context: Context,
) {

    suspend fun account(): Flow<Account> = prefs.getAsFlow().map {
        Account(
            title = it.accountTitle(),
            subtitle = it.accountSubtitle(),
        )
    }

    private fun Preferences.accountTitle(): String {
        return when (authType) {
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_APP,
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                resources.getString(R.string.nextcloud)
            }
            PreferencesRepository.AUTH_TYPE_STANDALONE -> {
                resources.getString(R.string.standalone_mode)
            }
            else -> ""
        }
    }

    private fun Preferences.accountSubtitle(): String {
        return when (authType) {
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_APP,
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                if (nextcloudServerUrl.isNotBlank()) {
                    val username = nextcloudServerUsername
                    "$username@${nextcloudServerUrl.replace("https://", "")}"
                } else {
                    try {
                        val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                        account.name
                    } catch (e: SSOException) {
                        Timber.e(e)
                        "unknown"
                    }
                }
            }
            PreferencesRepository.AUTH_TYPE_STANDALONE -> {
                ""
            }
            else -> ""
        }
    }

    data class Account(
        val title: String,
        val subtitle: String,
    )
}