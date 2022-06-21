package auth

import androidx.lifecycle.ViewModel
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AuthModel(
    private val confRepo: ConfRepository,
) : ViewModel() {

    suspend fun loadConf() = confRepo.load().first()

    suspend fun saveConf(newConf: (Conf) -> Conf) = confRepo.save { newConf(it) }

    suspend fun setBackend(newBackend: String) {
        confRepo.save { it.copy(backend = newBackend) }
    }
}