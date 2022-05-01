package common

import androidx.lifecycle.ViewModel
import auth.AccountsRepository

class AppViewModel(
    private val accountsRepository: AccountsRepository,
) : ViewModel() {

    fun account() = accountsRepository.account()
}