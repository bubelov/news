package settings

import androidx.lifecycle.ViewModel
import auth.accountSubtitle
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
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

    fun getConf() = confRepo.select()

    suspend fun saveConf(conf: Conf) = this.confRepo.upsert(conf)

    fun getAccountName(): String = runBlocking {
        db.confQueries
            .select()
            .asFlow()
            .mapToOneOrDefault(ConfRepository.DEFAULT_CONF)
            .map { it.accountSubtitle() }
            .first()
    }

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