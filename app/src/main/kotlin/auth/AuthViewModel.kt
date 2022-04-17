package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.ConfRepository
import db.Conf

class AuthViewModel(
    private val confRepo: ConfRepository,
    private val newsApiSwitcher: NewsApiSwitcher,
) : ViewModel() {

    fun selectConf() = confRepo.select()

    suspend fun upsertConf(conf: Conf) {
        confRepo.upsert(conf)
        newsApiSwitcher.switch(conf.authType)
    }
}