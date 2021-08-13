package log

import androidx.lifecycle.ViewModel
import db.Log
import kotlinx.coroutines.flow.MutableStateFlow

class LogViewModel(
    private val repository: LogRepository
) : ViewModel() {

    val state = MutableStateFlow<State>(State.Idle)

    suspend fun onViewReady() {
        if (state.value == State.Idle) {
            loadItems()
        }
    }

    suspend fun deleteAllItems() {
        repository.deleteAll()
        loadItems()
    }

    private suspend fun loadItems() {
        state.value = State.Loading
        state.value = State.Loaded(repository.selectAll())
    }

    sealed class State {
        object Idle : State()
        object Loading : State()
        data class Loaded(val items: List<Log>) : State()
    }
}