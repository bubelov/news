package co.appreactor.nextcloud.news

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

class StarredNewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences
) : ViewModel() {

    suspend fun getNewsItems() = newsItemsRepository.all().map { items ->
        return@map items.filter { it.starred }
    }

    suspend fun getFeeds() = newsFeedsRepository.all()

    suspend fun isInitialSyncCompleted() =
        prefs.getBoolean(Preferences.INITIAL_SYNC_COMPLETED, false)
}