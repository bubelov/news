package auth

import android.content.Context
import co.appreactor.news.R
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import common.ConfRepository
import db.Conf
import db.ConfQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountsRepository(
    private val confQueries: ConfQueries,
    private val context: Context,
) {

    private val resources = context.resources

    fun account(): Flow<Account> {
        return confQueries.select().asFlow().mapToOneOrNull().map {
            Account(
                title = it?.accountTitle() ?: "",
                subtitle = it?.accountSubtitle() ?: "",
            )
        }
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

    private suspend fun Conf.accountSubtitle(): String {
        return when (authType) {
            ConfRepository.AUTH_TYPE_NEXTCLOUD_APP,
            ConfRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                if (nextcloudServerUrl.isNotBlank()) {
                    val username = nextcloudServerUsername
                    "$username@${nextcloudServerUrl.replace("https://", "")}"
                } else {
                    try {
                        val account = withContext(Dispatchers.Default) {
                            SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                        }

                        account.name
                    } catch (e: SSOException) {
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
}