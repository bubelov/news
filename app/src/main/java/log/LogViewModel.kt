package log

import androidx.lifecycle.ViewModel
import db.Log
import kotlinx.coroutines.flow.MutableStateFlow

class LogViewModel(
    private val repo: LogRepository
) : ViewModel() {

    val state = MutableStateFlow<State?>(null)

    suspend fun onViewReady() {
        if (state.value == null) {
            reload()
        }
    }

    suspend fun deleteAll() {
        state.value = State.Deleting

        runCatching {
            repo.deleteAll()
        }.onSuccess {
            reload()
        }.onFailure {
            state.value = State.FailedToDelete(it.message ?: "")
        }
    }

    suspend fun reload() {
        state.value = State.Loading

        runCatching {
            state.value = State.Loaded(repo.selectAll())
        }.onFailure {
            state.value = State.FailedToLoad(it.message ?: "")
        }
    }

    sealed class State {
        object Loading : State()
        data class Loaded(val items: List<Log>) : State()
        data class FailedToLoad(val reason: String) : State()
        object Deleting : State()
        data class FailedToDelete(val reason: String) : State()
    }
}