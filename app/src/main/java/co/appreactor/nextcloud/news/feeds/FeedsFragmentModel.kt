package co.appreactor.nextcloud.news.feeds

import androidx.lifecycle.ViewModel

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository
) : ViewModel() {

    suspend fun getFeeds() = feedsRepository.all()
}