package log

import androidx.lifecycle.ViewModel

class ExceptionViewModel(
    private val repository: LogRepository,
) : ViewModel() {

    suspend fun selectById(id: String) = repository.selectById(id)
}