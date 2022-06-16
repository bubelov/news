package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AuthViewModel(
    private val confRepo: ConfRepository,
    private val apiSwitcher: NewsApiSwitcher,
) : ViewModel() {

    suspend fun loadConf() = confRepo.load().first()

    suspend fun saveConf(newConf: (Conf) -> Conf) = confRepo.save { newConf(it) }

    suspend fun setBackend(newBackend: String) {
        confRepo.save { it.copy(backend = newBackend) }
        apiSwitcher.switch(newBackend)
    }
}