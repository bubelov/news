package exception

import androidx.lifecycle.ViewModel
import exceptions.AppExceptionsRepository

class AppExceptionViewModel(
    private val repository: AppExceptionsRepository,
) : ViewModel() {

    suspend fun selectById(id: String) = repository.selectById(id)
}