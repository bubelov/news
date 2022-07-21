package settings

import androidx.lifecycle.ViewModel
import conf.ConfRepo
import db.Conf
import db.Db
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class SettingsModel(
    private val confRepo: ConfRepo,
    private val db: Db,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    fun loadConf() = confRepo.conf

    fun saveConf(newConf: (Conf) -> Conf) = this.confRepo.update(newConf)

    fun getAccountName(): String {
        val conf = confRepo.conf.value

        return when (conf.backend) {
            ConfRepo.BACKEND_STANDALONE -> ""
            ConfRepo.BACKEND_MINIFLUX -> {
                val username = conf.minifluxServerUsername
                "$username@${conf.minifluxServerUrl.extractDomain()}"
            }
            ConfRepo.BACKEND_NEXTCLOUD -> {
                val username = conf.nextcloudServerUsername
                "$username@${conf.nextcloudServerUrl.extractDomain()}"
            }
            else -> ""
        }
    }

    fun scheduleBackgroundSync() {
        syncScheduler.schedule()
    }

    fun logOut() {
        confRepo.update { ConfRepo.DEFAULT_CONF }

        db.apply {
            transaction {
                feedQueries.deleteAll()
                entryQueries.deleteAll()
            }
        }
    }

    private fun String.extractDomain(): String {
        return replace("https://", "").replace("http://", "")
    }
}