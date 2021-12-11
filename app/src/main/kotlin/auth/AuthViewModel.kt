package auth

import androidx.lifecycle.ViewModel
import api.NewsApiSwitcher
import common.ConfRepository
import db.Conf

class AuthViewModel(
    private val newsApiSwitcher: NewsApiSwitcher,
    private val conf: ConfRepository,
) : ViewModel() {

    suspend fun getConf() = conf.get()

    suspend fun saveConf(conf: Conf) {
        val oldConf = getConf()
        this.conf.save(conf)

        if (conf.authType != oldConf.authType) {
            newsApiSwitcher.switch(conf.authType)
        }
    }
}