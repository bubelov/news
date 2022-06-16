package settings

import androidx.lifecycle.ViewModel
import auth.accountSubtitle
import common.ConfRepository
import db.Conf
import db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class SettingsViewModel(
    private val confRepo: ConfRepository,
    private val db: Database,
) : ViewModel() {

    fun loadConf() = confRepo.load()

    suspend fun saveConf(newConf: (Conf) -> Conf) = this.confRepo.save(newConf)

    fun getAccountName(): String = runBlocking {
        loadConf().map { it.accountSubtitle() }.first()
    }

    fun logOut() {
        db.apply {
            transaction {
                confQueries.deleteAll()
                feedQueries.deleteAll()
                entryQueries.deleteAll()
            }
        }
    }
}