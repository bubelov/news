package auth

import android.content.Context
import co.appreactor.news.R
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import common.ConfRepository
import db.Conf
import db.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class AccountsRepository(
    private val db: Database,
    private val context: Context,
) {

    private val resources = context.resources

    fun account(): Flow<Account> {
        return db.confQueries.select().asFlow().mapToOneOrNull().map {
            Account(
                title = it?.accountTitle() ?: "",
                subtitle = it?.accountSubtitle() ?: "",
            )
        }
    }

    private fun Conf.accountTitle(): String {
        return when (backend) {
            ConfRepository.BACKEND_STANDALONE -> resources.getString(R.string.standalone_mode)
            ConfRepository.BACKEND_MINIFLUX -> resources.getString(R.string.miniflux)
            ConfRepository.BACKEND_NEXTCLOUD -> resources.getString(R.string.nextcloud)
            else -> ""
        }
    }

    private suspend fun Conf.accountSubtitle(): String {
        return when (backend) {
            ConfRepository.BACKEND_STANDALONE -> ""
            ConfRepository.BACKEND_MINIFLUX -> {
                val username = minifluxServerUsername
                "$username@${minifluxServerUrl.replace("https://", "")}"
            }
            ConfRepository.BACKEND_NEXTCLOUD -> {
                val username = nextcloudServerUsername
                "$username@${nextcloudServerUrl.replace("https://", "")}"
            }
            else -> ""
        }
    }
}