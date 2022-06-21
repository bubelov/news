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
import sync.BackgroundSyncScheduler

@KoinViewModel
class SettingsViewModel(
    private val confRepo: ConfRepository,
    private val db: Database,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    fun loadConf() = confRepo.load()

    suspend fun saveConf(newConf: (Conf) -> Conf) = this.confRepo.save(newConf)

    fun getAccountName(): String = runBlocking {
        loadConf().map { it.accountSubtitle() }.first()
    }

    suspend fun scheduleBackgroundSync() {
        syncScheduler.schedule(override = true)
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