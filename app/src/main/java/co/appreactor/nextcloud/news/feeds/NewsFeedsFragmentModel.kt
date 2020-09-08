package co.appreactor.nextcloud.news.feeds

import androidx.lifecycle.ViewModel

class NewsFeedsFragmentModel(
    private val newsFeedsRepository: NewsFeedsRepository
) : ViewModel() {

    suspend fun getFeeds() = newsFeedsRepository.all()
}