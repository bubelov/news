package settings

import androidx.lifecycle.ViewModel
import conf.ConfRepo
import db.Conf
import db.Db
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import navigation.accountSubtitle
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class SettingsViewModel(
    private val confRepo: ConfRepo,
    private val db: Db,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    fun loadConf() = confRepo.conf

    fun saveConf(newConf: (Conf) -> Conf) = this.confRepo.update(newConf)

    fun getAccountName(): String = runBlocking {
        loadConf().map { it.accountSubtitle() }.first()
    }

    fun scheduleBackgroundSync() {
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