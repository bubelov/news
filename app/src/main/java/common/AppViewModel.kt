package common

import androidx.lifecycle.ViewModel
import auth.AuthRepository

class AppViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    suspend fun account() = authRepository.account()
}