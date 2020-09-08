package co.appreactor.nextcloud.news.logging

import androidx.lifecycle.ViewModel

class ExceptionsFragmentModel(
    private val exceptionsRepository: ExceptionsRepository
) : ViewModel() {

    suspend fun getExceptions() = exceptionsRepository.all()
}