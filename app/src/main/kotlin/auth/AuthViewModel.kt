package auth

import androidx.lifecycle.ViewModel
import common.ConfRepository
import db.Conf

class AuthViewModel(
    private val confRepo: ConfRepository,
) : ViewModel() {

    fun selectConf() = confRepo.select()

    suspend fun upsertConf(conf: Conf) {
        confRepo.upsert(conf)
    }
}