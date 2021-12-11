package common

sealed class Result<out T> {
    object Inactive : Result<Nothing>()
    object Progress : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val throwable: Throwable) : Result<Nothing>()
}
