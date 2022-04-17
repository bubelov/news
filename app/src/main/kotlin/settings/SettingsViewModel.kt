package settings

import androidx.lifecycle.ViewModel
import auth.AuthRepository
import db.Database
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    private val confRepo: ConfRepository,
    private val authRepository: AuthRepository,
    private val db: Database,
) : ViewModel() {

    fun getConf() = confRepo.select()

    suspend fun saveConf(conf: Conf) = this.confRepo.upsert(conf)

    fun getAccountName(): String = runBlocking { authRepository.account().first().subtitle }

    fun logOut() {
        db.apply {
            transaction {
                entryQueries.deleteAll()
                entryEnclosureQueries.deleteAll()
                entryImageQueries.deleteAll()
                entryImagesMetadataQueries.deleteAll()
                feedQueries.deleteAll()
                confQueries.delete()
            }
        }
    }
}