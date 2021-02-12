package logging

import androidx.lifecycle.ViewModel
import common.Result
import db.LoggedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class LoggedExceptionsFragmentModel(
    private val repository: LoggedExceptionsRepository
) : ViewModel() {

    val items = MutableStateFlow<Result<List<LoggedException>>>(Result.Inactive)

    suspend fun onViewReady() {
        if (items.value == Result.Inactive) {
            queryItems()
        }
    }

    suspend fun deleteAllItems() {
        repository.deleteAll()
        queryItems()
    }

    private suspend fun queryItems() {
        items.value = Result.Progress

        runCatching {
            items.value = Result.Success(repository.all().first())
        }.onFailure {
            items.value = Result.Failure(it)
        }
    }
}