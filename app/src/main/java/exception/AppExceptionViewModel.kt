package exception

import androidx.lifecycle.ViewModel
import exceptions.AppExceptionsRepository

class AppExceptionViewModel(
    private val repository: AppExceptionsRepository,
) : ViewModel() {

    suspend fun select(id: String) = repository.select(id)
}