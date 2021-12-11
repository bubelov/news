package settings

import androidx.lifecycle.ViewModel
import auth.AuthRepository
import db.Database
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    private val conf: ConfRepository,
    private val authRepository: AuthRepository,
    private val db: Database,
) : ViewModel() {

    suspend fun getConf() = conf.get()

    suspend fun saveConf(conf: Conf) = this.conf.save(conf)

    fun getAccountName(): String = runBlocking { authRepository.account().first().subtitle }

    fun logOut() {
        db.apply {
            transaction {
                entryQueries.deleteAll()
                entryEnclosureQueries.deleteAll()
                entryImageQueries.deleteAll()
                entryImagesMetadataQueries.deleteAll()
                feedQueries.deleteAll()
                logQueries.deleteAll()
                confQueries.deleteAll()
            }
        }
    }
}