package exception

import androidx.lifecycle.ViewModel
import exceptions.AppExceptionsRepository

class AppExceptionFragmentModel(
    private val repository: AppExceptionsRepository,
) : ViewModel() {

    suspend fun select(id: String) = repository.select(id)
}