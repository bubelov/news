package co.appreactor.nextcloud.news.feeds

import androidx.lifecycle.ViewModel

class FeedsFragmentModel(
    private val feedsRepository: FeedsRepository
) : ViewModel() {

    suspend fun getFeeds() = feedsRepository.all()

    suspend fun deleteFeed(id: Long) {
        feedsRepository.deleteById(id)
    }
}