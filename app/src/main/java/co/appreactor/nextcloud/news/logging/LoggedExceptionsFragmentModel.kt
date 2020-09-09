package co.appreactor.nextcloud.news.logging

import androidx.lifecycle.ViewModel

class LoggedExceptionsFragmentModel(
    private val loggedExceptionsRepository: LoggedExceptionsRepository
) : ViewModel() {

    suspend fun getExceptions() = loggedExceptionsRepository.all()

    suspend fun deleteAll() = loggedExceptionsRepository.deleteAll()
}