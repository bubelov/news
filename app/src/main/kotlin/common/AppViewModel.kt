package common

import androidx.lifecycle.ViewModel
import auth.AccountsRepository
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AppViewModel(
    private val accountsRepository: AccountsRepository,
) : ViewModel() {

    fun account() = accountsRepository.account()
}