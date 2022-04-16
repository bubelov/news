package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.ConfRepository
import db.Conf
import kotlinx.coroutines.flow.first

class AuthViewModel(
    private val newsApiSwitcher: NewsApiSwitcher,
    private val confRepo: ConfRepository,
) : ViewModel() {

    fun getConf() = confRepo.select()

    suspend fun saveConf(newConf: Conf) {
        val oldConf = getConf().first()
        confRepo.insert(newConf)

        if (newConf.authType != oldConf.authType) {
            newsApiSwitcher.switch(newConf.authType)
        }
    }
}