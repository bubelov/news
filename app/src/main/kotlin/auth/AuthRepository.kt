package auth

import android.content.Context
import android.content.res.Resources
import co.appreactor.news.R
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class AuthRepository(
    private val conf: ConfRepository,
    private val resources: Resources,
    private val context: Context,
) {

    suspend fun account(): Flow<Account> = conf.getAsFlow().map {
        Account(
            title = it.accountTitle(),
            subtitle = it.accountSubtitle(),
        )
    }

    private fun Conf.accountTitle(): String {
        return when (authType) {
            ConfRepository.AUTH_TYPE_NEXTCLOUD_APP,
            ConfRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                resources.getString(R.string.nextcloud)
            }
            ConfRepository.AUTH_TYPE_MINIFLUX -> {
                resources.getString(R.string.miniflux)
            }
            ConfRepository.AUTH_TYPE_STANDALONE -> {
                resources.getString(R.string.standalone_mode_no_beta)
            }
            else -> ""
        }
    }

    private fun Conf.accountSubtitle(): String {
        return when (authType) {
            ConfRepository.AUTH_TYPE_NEXTCLOUD_APP,
            ConfRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
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
            ConfRepository.AUTH_TYPE_MINIFLUX -> {
                val username = minifluxServerUsername
                "$username@${minifluxServerUrl.replace("https://", "")}"
            }
            ConfRepository.AUTH_TYPE_STANDALONE -> {
                resources.getString(R.string.beta)
            }
            else -> ""
        }
    }

    data class Account(
        val title: String,
        val subtitle: String,
    )
}