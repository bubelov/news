package co.appreactor.nextcloud.news.starred

import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.NewsFeedsRepository
import co.appreactor.nextcloud.news.Preferences
import co.appreactor.nextcloud.news.news.NewsAdapterRow
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import kotlinx.coroutines.flow.*
import java.text.DateFormat
import java.util.*

class StarredNewsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences
) : ViewModel() {

    suspend fun getNewsItems() = newsItemsRepository.all().map { unfilteredItems ->
        val items = unfilteredItems.filter { it.starred }
        val feeds = newsFeedsRepository.all()

        items.map {
            val dateString = DateFormat.getDateInstance().format(Date(it.pubDate * 1000))
            val feed = feeds.single { feed -> feed.id == it.feedId }

            NewsAdapterRow(
                it.id,
                it.title,
                feed.title + " · " + dateString,
                it.summary,
                true
            )
        }
    }

    suspend fun isInitialSyncCompleted() =
        prefs.getBoolean(Preferences.INITIAL_SYNC_COMPLETED, false)
}