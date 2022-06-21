package auth

import androidx.lifecycle.ViewModel
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class AuthModel(
    private val confRepo: ConfRepository,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun loadConf() = confRepo.load().first()

    suspend fun saveConf(newConf: (Conf) -> Conf) = confRepo.save { newConf(it) }

    suspend fun setBackend(newBackend: String) {
        confRepo.save { it.copy(backend = newBackend) }
    }

    suspend fun scheduleBackgroundSync() {
        syncScheduler.schedule(override = true)
    }
}