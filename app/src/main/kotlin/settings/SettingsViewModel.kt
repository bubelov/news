package settings

import androidx.lifecycle.ViewModel
import auth.AccountsRepository
import common.ConfRepository
import db.Conf
import db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class SettingsViewModel(
    private val confRepo: ConfRepository,
    private val accountsRepository: AccountsRepository,
    private val db: Database,
) : ViewModel() {

    fun getConf() = confRepo.select()

    suspend fun saveConf(conf: Conf) = this.confRepo.upsert(conf)

    fun getAccountName(): String = runBlocking { accountsRepository.account().first().subtitle }

    fun logOut() {
        db.apply {
            transaction {
                confQueries.delete()
                feedQueries.deleteAll()
                entryQueries.deleteAll()
            }
        }
    }
}